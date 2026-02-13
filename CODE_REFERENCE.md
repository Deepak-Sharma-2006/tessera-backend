# Implementation Code Reference

## Quick Copy-Paste Guide

All code changes are already implemented. This document shows exact code for reference.

---

## 1. Post.java - New Field

```java
// Location: End of Post class, before closing brace

@Indexed // ✅ Domain-locked institutional isolation: Email domain from author's email
private String institutionDomain; // e.g., "sinhgad.edu", "coep.ac.in"
```

---

## 2. PostRepository.java - New Query Method

```java
// Location: Inside PostRepository interface, after findByCollege method

// ✅ DOMAIN-LOCKED INSTITUTIONAL ISOLATION: Find posts by email domain
List<Post> findByInstitutionDomain(String institutionDomain);
```

---

## 3. PostService.java - Domain Extraction in createPost()

### Updated Code Block (Replace existing section):

```java
// ✅ Campus Isolation: Fetch author and set college
try {
    com.studencollabfin.server.model.User author = userService.getUserById(authorId);
    if (author != null && author.getCollegeName() != null) {
        post.setCollege(author.getCollegeName());
        System.out.println("✅ Post college set to: " + author.getCollegeName());
    }

    // ✅ DOMAIN-LOCKED INSTITUTIONAL ISOLATION: Extract domain from author's email
    if (author != null && author.getEmail() != null && !author.getEmail().isEmpty()) {
        String institutionDomain = extractDomainFromEmail(author.getEmail());
        post.setInstitutionDomain(institutionDomain);
        System.out.println("✅ Post institution domain set to: " + institutionDomain + " (from email: " + author.getEmail() + ")");
    } else {
        System.err.println("⚠️ Author email not found for domain extraction");
    }
} catch (Exception ex) {
    System.err.println("⚠️ Failed to fetch author for college/domain assignment: " + ex.getMessage());
}
```

---

## 3. PostService.java - New Method: getCampusPostsByDomain

```java
// Location: After getAllPosts(String userCollegeName) method

/**
 * ✅ DOMAIN-LOCKED INSTITUTIONAL ISOLATION: Fetch posts by email domain
 * Ensures strict 1:1 campus silo - student from "sinhgad.edu" never receives posts from "coep.ac.in"
 *
 * @param institutionDomain The email domain (e.g., "sinhgad.edu", "coep.ac.in")
 * @return List of posts from the same institution, or empty list if domain is invalid
 */
public List<Post> getCampusPostsByDomain(String institutionDomain) {
    if (institutionDomain == null || institutionDomain.trim().isEmpty()) {
        System.err.println("⚠️ Institution domain is null/empty, returning empty post list for domain isolation");
        return new java.util.ArrayList<>();
    }

    List<Post> domainPosts = postRepository.findByInstitutionDomain(institutionDomain);
    System.out.println("✅ Fetched " + domainPosts.size() + " posts for domain: " + institutionDomain);
    return domainPosts;
}
```

---

## 3. PostService.java - New Utility: extractDomainFromEmail

```java
// Location: At the very end of PostService class, before closing brace

/**
 * ✅ DOMAIN-LOCKED INSTITUTIONAL ISOLATION: Extract email domain
 * Converts "sara@sinhgad.edu" → "sinhgad.edu"
 * Converts "user@coep.ac.in" → "coep.ac.in"
 *
 * @param email The user's email address
 * @return The domain portion of the email, or empty string if invalid
 */
private String extractDomainFromEmail(String email) {
    if (email == null || !email.contains("@")) {
        return "";
    }
    String domain = email.substring(email.indexOf("@") + 1).toLowerCase().trim();
    return domain.isEmpty() ? "" : domain;
}
```

---

## 4. PostController.java - New Utility: extractDomainFromEmail

```java
// Location: After getCurrentUserId method

/**
 * ✅ DOMAIN-LOCKED INSTITUTIONAL ISOLATION: Extract email domain
 * Converts "sara@sinhgad.edu" → "sinhgad.edu"
 *
 * @param email The user's email address
 * @return The domain portion of the email, or empty string if invalid
 */
private String extractDomainFromEmail(String email) {
    if (email == null || !email.contains("@")) {
        return "";
    }
    String domain = email.substring(email.indexOf("@") + 1).toLowerCase().trim();
    return domain.isEmpty() ? "" : domain;
}
```

---

## 4. PostController.java - Updated getCampusPosts Method

```java
// Replace entire getCampusPosts method with this:

// Separate endpoints for Campus Feed posts
@GetMapping("/campus")
public ResponseEntity<List<Object>> getCampusPosts(@RequestParam(required = false) String type,
        Authentication authentication, HttpServletRequest request) {
    try {
        // ✅ DOMAIN-LOCKED INSTITUTIONAL ISOLATION: Extract user's email domain
        String userId = getCurrentUserId(authentication, request);
        String institutionDomain = null;

        if (userId != null && !userId.trim().isEmpty()) {
            try {
                com.studencollabfin.server.model.User currentUser = userService.getUserById(userId);
                if (currentUser != null && currentUser.getEmail() != null) {
                    institutionDomain = extractDomainFromEmail(currentUser.getEmail());
                    if (institutionDomain != null && !institutionDomain.isEmpty()) {
                        System.out.println("✅ Campus Feed: User domain authenticated: " + institutionDomain);
                    } else {
                        System.err.println("⚠️ Campus Feed: Could not extract domain from email: " + currentUser.getEmail());
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(java.util.List.of(java.util.Map.of("error", "User email domain not found")));
                    }
                } else {
                    System.err.println("⚠️ Campus Feed: User or email not found");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(java.util.List.of(java.util.Map.of("error", "User not authenticated")));
                }
            } catch (Exception ex) {
                System.err.println("⚠️ Campus Feed: Error fetching user: " + ex.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.List.of(java.util.Map.of("error", "Error fetching user")));
            }
        } else {
            System.err.println("⚠️ Campus Feed: No userId found");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(java.util.List.of(java.util.Map.of("error", "User ID not found")));
        }

        // Fetch posts strictly by institution domain
        List<Post> posts = postService.getCampusPostsByDomain(institutionDomain);
        System.out.println("Total campus posts for domain " + institutionDomain + ": " + posts.size());

        java.util.List<com.studencollabfin.server.model.PostType> campusTypes = java.util.Arrays.asList(
                com.studencollabfin.server.model.PostType.ASK_HELP,
                com.studencollabfin.server.model.PostType.OFFER_HELP,
                com.studencollabfin.server.model.PostType.LOOKING_FOR,
                com.studencollabfin.server.model.PostType.POLL);

        // Filter for campus posts only - by type and category
        java.util.List<Post> campusPosts = new java.util.ArrayList<>();
        for (Post post : posts) {
            if (post instanceof com.studencollabfin.server.model.SocialPost) {
                com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                // Include posts with CAMPUS category or no category set (backward
                // compatibility)
                String category = social.getCategory() != null ? social.getCategory() : "CAMPUS";
                if (campusTypes.contains(social.getType()) && ("CAMPUS".equals(category) || category == null)) {
                    campusPosts.add(post);
                }
            } else if (post instanceof com.studencollabfin.server.model.TeamFindingPost) {
                campusPosts.add(post);
            }
        }
        System.out.println("Campus posts filtered by type: " + campusPosts.size());

        // Apply type filter if provided
        if (type != null && !type.isBlank()) {
            try {
                com.studencollabfin.server.model.PostType ptype = com.studencollabfin.server.model.PostType
                        .valueOf(type);
                java.util.List<Post> filtered = new java.util.ArrayList<>();
                for (Post post : campusPosts) {
                    if (post instanceof com.studencollabfin.server.model.SocialPost) {
                        com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                        if (social.getType() == ptype)
                            filtered.add(post);
                    }
                }
                campusPosts = filtered;
            } catch (IllegalArgumentException ex) {
                // unknown type - ignore
            }
        }

        return ResponseEntity.ok(convertToRichPosts(campusPosts));
    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(500).body(new java.util.ArrayList<>());
    }
}
```

---

## 4. PostController.java - Updated getPostById Method

```java
// Replace entire getPostById method with this:

@GetMapping("/{id}")
public ResponseEntity<Object> getPostById(@PathVariable String id, Authentication authentication,
        HttpServletRequest request) {
    try {
        Post post = postService.getPostById(id);

        // ✅ SECURITY LAYER: Verify user's domain matches post's domain (institutional isolation)
        String userId = getCurrentUserId(authentication, request);
        if (userId != null && !userId.trim().isEmpty()) {
            try {
                com.studencollabfin.server.model.User currentUser = userService.getUserById(userId);
                if (currentUser != null && currentUser.getEmail() != null && post.getInstitutionDomain() != null) {
                    String userDomain = extractDomainFromEmail(currentUser.getEmail());
                    String postDomain = post.getInstitutionDomain();

                    // Cross-domain access denied
                    if (!userDomain.equals(postDomain)) {
                        System.err.println("🔒 SECURITY: Cross-domain access DENIED - User domain: " + userDomain
                            + ", Post domain: " + postDomain);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(java.util.Map.of("error", "Cross-domain post access denied",
                                "userDomain", userDomain,
                                "postDomain", postDomain));
                    }
                    System.out.println("✅ SECURITY: Domain verification PASSED for user: " + userId
                        + " accessing post in domain: " + postDomain);
                }
            } catch (Exception ex) {
                System.err.println("⚠️ SECURITY: Error verifying domain: " + ex.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "Error verifying post access"));
            }
        }

        java.util.Map<String, Object> richPost = new java.util.HashMap<>();
        richPost.put("id", post.getId());
        richPost.put("authorId", post.getAuthorId());
        richPost.put("createdAt", post.getCreatedAt() != null ? post.getCreatedAt().toString() : "");
        richPost.put("institutionDomain", post.getInstitutionDomain());
        if (post instanceof com.studencollabfin.server.model.SocialPost) {
            com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
            richPost.put("title", social.getTitle() != null ? social.getTitle() : "");
            richPost.put("content", social.getContent());
            richPost.put("type", social.getType() != null ? social.getType().name() : "");
            richPost.put("postType", social.getType() != null ? social.getType().name() : "");
            richPost.put("likes", social.getLikes() != null ? social.getLikes() : new java.util.ArrayList<>());
            richPost.put("commentIds",
                    social.getCommentIds() != null ? social.getCommentIds() : new java.util.ArrayList<>());
            richPost.put("pollOptions",
                    social.getPollOptions() != null ? social.getPollOptions() : new java.util.ArrayList<>());
            richPost.put("requiredSkills",
                    social.getRequiredSkills() != null ? social.getRequiredSkills() : new java.util.ArrayList<>());
        } else if (post instanceof com.studencollabfin.server.model.TeamFindingPost) {
            com.studencollabfin.server.model.TeamFindingPost team = (com.studencollabfin.server.model.TeamFindingPost) post;
            richPost.put("title", team.getContent());
            richPost.put("content", team.getContent());
            richPost.put("type", "LOOKING_FOR");
            richPost.put("postType", "LOOKING_FOR");
            richPost.put("requiredSkills",
                    team.getRequiredSkills() != null ? team.getRequiredSkills() : new java.util.ArrayList<>());
            richPost.put("maxTeamSize", team.getMaxTeamSize());
            richPost.put("currentTeamMembers", team.getCurrentTeamMembers() != null ? team.getCurrentTeamMembers()
                    : new java.util.ArrayList<>());
        } else {
            richPost.put("title", post.getContent());
            richPost.put("content", post.getContent());
            richPost.put("type", "GENERAL");
        }
        addAuthorDetailsToPost(richPost, post.getAuthorId());
        return ResponseEntity.ok(richPost);
    } catch (java.util.NoSuchElementException ex) {
        System.err.println("Post " + id + " not found");
        return ResponseEntity.notFound().build();
    } catch (Exception e) {
        System.err.println("Error getting post: " + e.getMessage());
        return ResponseEntity.status(500).build();
    }
}
```

---

## MongoDB Index Creation

Run this in MongoDB console to create the index:

```javascript
db.posts.createIndex({ institutionDomain: 1 });
```

Or in Mongosh:

```javascript
use tessera_db
db.posts.createIndex({ institutionDomain: 1 })
db.posts.getIndexes()  // Verify index is created
```

---

## MongoDB Migration (for existing posts)

Update any existing posts that don't have institutionDomain set:

```javascript
db.posts.updateMany(
  { institutionDomain: { $exists: false } },
  { $set: { institutionDomain: "" } },
);

// Verify:
db.posts.countDocuments({ institutionDomain: { $exists: false } }); // Should return 0
```

---

## Testing Commands

### Test 1: Verify Post Creation Sets Domain

```bash
curl -X POST http://localhost:8080/api/posts/social \
  -H "X-User-Id: user123" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test Post",
    "content": "Testing domain isolation",
    "type": "ASK_HELP"
  }'
```

**Expected Response**: Post includes `"institutionDomain": "sinhgad.edu"`

### Test 2: Campus Feed Returns Domain-Filtered Posts

```bash
curl http://localhost:8080/api/posts/campus \
  -H "X-User-Id: sara@sinhgad.edu"
```

**Expected Response**: Only posts with `"institutionDomain": "sinhgad.edu"`

### Test 3: Cross-Domain Direct Access Blocked

```bash
# User from coep.ac.in tries to access post from sinhgad.edu
curl http://localhost:8080/api/posts/postId123 \
  -H "X-User-Id: student@coep.ac.in"
```

**Expected Response**: HTTP 403 Forbidden

```json
{
  "error": "Cross-domain post access denied",
  "userDomain": "coep.ac.in",
  "postDomain": "sinhgad.edu"
}
```

---

## Verification Checklist

- [ ] All 4 files compile with `mvn clean compile`
- [ ] MongoDB index created
- [ ] Existing posts migrated
- [ ] Test 1 passes (domain in response)
- [ ] Test 2 passes (campus feed filtered)
- [ ] Test 3 passes (cross-domain blocked)
- [ ] Logs show domain verification messages
- [ ] No compilation errors

---

**Implementation Ready for Deployment ✅**
