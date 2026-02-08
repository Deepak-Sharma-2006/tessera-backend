# Backend Audit Findings: 401 Unauthorized & Feed Response Format

## 1. Token Configuration

### JwtUtil.java (Line 19)
```java
private static final long JWT_TOKEN_VALIDITY = 5 * 60 * 60; // 5 hours
```

**Finding**: Tokens are set to expire in **5 hours**. This is sufficient for normal app usage and should NOT be causing immediate 401 errors.

### AuthenticationController.java (Lines 31-50)
```java
final String jwt = jwtUtil.generateToken(user.getEmail());
Cookie cookie = new Cookie("token", jwt);
cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
```

**Finding**: 
- JWT token: 5 hours validity
- HTTP cookie: 7 days max age
- Mismatch: If using cookie-based auth, token expires before cookie is cleared

## 2. Security Configuration

### SecurityConfig.java (Lines 29-35)
```java
.authorizeHttpRequests(auth -> auth
    // Explicitly allow preflight OPTIONS requests for CORS
    .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
    // Allow all other requests (For Dev Only)
    .anyRequest().permitAll())
```

**🔴 CRITICAL FINDING**: 
- **ALL endpoints allow anonymous access** (`anyRequest().permitAll()`)
- NO endpoint requires authentication in backend
- 401 errors are NOT coming from backend security filter
- Backend is configured for **development only**

### JwtRequestFilter.java (Line 21)
```java
// NOTE: This component remains disabled to prevent conflicts with OAuth2 security.
// To use JWT security instead of OAuth2, you would uncomment the line below.
// @Component
```

**Finding**: JWT authentication filter is DISABLED. Authentication is not actively validated.

## 3. Profile Update Endpoint

### UserController.java (Lines 52-60)
```java
@PutMapping("/{userId}")
public ResponseEntity<?> updateUserProfile(@PathVariable String userId, @RequestBody User profileData) {
    try {
        User updatedUser = userService.updateUserProfile(userId, profileData);
        User syncedUser = achievementService.syncUserBadges(updatedUser);
        return ResponseEntity.ok(syncedUser);
    } catch (RuntimeException e) {
        return ResponseEntity.notFound().build();
    }
}
```

**Finding**:
- Endpoint has NO authentication requirement
- Returns complete User object with updated fields
- No token refresh on profile update
- Exception handling returns 404 (not 401)

## 4. Get Campus Posts Response Format

### PostController.java (Lines 339, 683-729)
```java
@GetMapping("/campus")
public ResponseEntity<?> getCampusPosts(...) {
    // ... logic ...
    return ResponseEntity.ok(convertToRichPosts(campusPosts));
}

private List<Object> convertToRichPosts(List<?> posts) {
    List<Object> richPosts = new ArrayList<>();
    for (Object post : posts) {
        Map<String, Object> postMap = new HashMap<>();
        postMap.put("id", ...)
        postMap.put("authorId", ...)
        postMap.put("createdAt", ...)
        // ... more fields ...
        richPosts.add(postMap);
    }
    return richPosts;
}
```

**🟢 CONFIRMED**: Backend returns **flat List<Map<String, Object>>**, NOT wrapped.

Response structure:
```json
[
  {
    "id": "...",
    "authorId": "...",
    "createdAt": "...",
    "title": "...",
    "content": "...",
    ...
  },
  ...
]
```

**NOT**: `{ "data": [...] }` or `{ "posts": [...] }`

## 5. Root Cause Analysis: 401 Unauthorized Loop

### Why Backend Config Allows All Requests
Backend SecurityConfig allows `.anyRequest().permitAll()` **intentionally for development**. This means:
- ✅ Profile update: Should NOT return 401
- ✅ Get posts: Should NOT return 401
- ✅ Any endpoint: Should NOT return 401

### Where 401 Actually Comes From
If user sees 401 loop after profile update:

**Option A**: Frontend Token Handling Bug
- Dio HTTP client not sending Bearer token correctly
- Token corrupted during storage/retrieval
- Token interceptor failing silently

**Option B**: CORS / Preflight Request
- OPTIONS preflight requests failing (returns 401)
- Chrome/browser blocking requests as CORS error
- Frontend sees network error as 401

**Option C**: Flutter App Token Storage
- isardb or shared_preferences not persisting token properly
- Token cleared during profile update
- Subsequent requests have no token

### Most Likely Cause
Profile update is **successful** but:
1. Frontend receives 200 OK response with updated User
2. Frontend attempts to fetch posts with stored token
3. Token is invalid or malformed in storage
4. Dio sends invalid Authorization header
5. Backend logs error but still returns 200 (due to .anyRequest().permitAll())
6. Frontend receives 401 **from somewhere else** (not backend)

## Recommendations

1. **Implement Backend Token Validation** (Production):
   - Uncomment JwtRequestFilter
   - Add @Component annotation
   - Return 401 for expired/invalid tokens
   - Log authentication failures

2. **Add Defensive 401 Handling** (Frontend - IMMEDIATE):
   - Check HTTP status codes in Dio interceptor
   - Logout on 401 response
   - Clear token from storage
   - Redirect to login
   - Prevent infinite loop

3. **Token Refresh Strategy**:
   - Generate new token after profile update
   - Return new token in profile update response
   - Frontend updates local token immediately
   - Old 5-hour expiry is acceptable for access token

4. **Profile Update Response Suggestion**:
```json
{
  "user": { users fields here },
  "token": "eyJhbGciOiJIUzI1NiIs..." // NEW: Return fresh token
}
```

## Summary

| Finding | Status | Impact |
|---------|--------|--------|
| Token expiry (5h) | ✅ Normal | Should NOT cause immediate 401 |
| Security filter disabled | ⚠️ Dev Only | No auth enforcement on backend |
| Posts response format | ✅ Flat List | Frontend parsing needs direct List handling |
| Profile update endpoint | ✅ No auth required | Should NOT return 401 |
| 401 Loop likely cause | 🔴 Frontend issue | Token handling or Dio interceptor bug |

---

**Backend Status**: ✅ Configured correctly for development
**Next Action**: Add defensive 401 handler to frontend Dio/post_provider
