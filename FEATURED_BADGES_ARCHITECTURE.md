# Featured Badges - System Architecture

## Component Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        TESSERA PLATFORM                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │             FRONTEND (React/Vite)                        │  │
│  ├──────────────────────────────────────────────────────────┤  │
│  │                                                          │  │
│  │  ┌────────────────────────────────────────────────────┐ │  │
│  │  │  BadgeCenter.jsx (Main Component)                 │ │  │
│  │  ├────────────────────────────────────────────────────┤ │  │
│  │  │                                                    │ │  │
│  │  │  ┌─ STATE ─────────────────────────────────────┐  │ │  │
│  │  │  │ • showFeaturedBadgeModal                    │  │ │  │
│  │  │  │ • featuredBadgesLoading                     │  │ │  │
│  │  │  │ • selectedBadge                             │  │ │  │
│  │  │  └─────────────────────────────────────────────┘  │ │  │
│  │  │                                                    │ │  │
│  │  │  ┌─ HANDLERS ──────────────────────────────────┐  │ │  │
│  │  │  │ • handleSelectFeaturedBadge()              │  │ │  │
│  │  │  │   - Validate badge unlock                 │  │ │  │
│  │  │  │   - Check max 2 limit                     │  │ │  │
│  │  │  │   - PUT /api/users/.../featured-badges   │  │ │  │
│  │  │  │   - Optimistic update                     │  │ │  │
│  │  │  └─────────────────────────────────────────────┘  │ │  │
│  │  │                                                    │ │  │
│  │  │  ┌─ UI COMPONENTS ──────────────────────────────┐ │ │  │
│  │  │  │ 1. Featured Badges Display (max 2)         │ │ │  │
│  │  │  │    • Shows earned badges with isActive     │ │ │  │
│  │  │  │    • Display badge icons + tier stars      │ │ │  │
│  │  │  │                                             │ │ │  │
│  │  │  │ 2. Empty Slots (clickable)                 │ │ │  │
│  │  │  │    • onClick → setShowFeaturedBadgeModal   │ │ │  │
│  │  │  │    • Hover effects                         │ │ │  │
│  │  │  │                                             │ │ │  │
│  │  │  │ 3. Selection Modal                         │ │ │  │
│  │  │  │    • Filter: earnedBadges                  │ │ │  │
│  │  │  │    • Filter: !isActive (not featured)      │ │ │  │
│  │  │  │    • Grid: 2-3 columns responsive          │ │ │  │
│  │  │  │    • Click: handleSelectFeaturedBadge()    │ │ │  │
│  │  │  └─────────────────────────────────────────────┘  │ │  │
│  │  │                                                    │ │  │
│  │  └────────────────────────────────────────────────────┘  │  │
│  │                         ▲                                  │  │
│  │                         │ setUser()                        │  │
│  │                         │ (parent state)                   │  │
│  │                                                            │  │
│  │  ┌────────────────────────────────────────────────────┐   │  │
│  │  │  ProfilePage.jsx (Public Profile)                 │   │  │
│  │  ├────────────────────────────────────────────────────┤   │  │
│  │  │                                                    │   │  │
│  │  │  ┌─ PUBLIC PROFILE SECTION ──────────────────┐   │   │  │
│  │  │  │ Title: ⭐ Featured Achievements          │   │   │  │
│  │  │  │                                           │   │   │  │
│  │  │  │ IF profileOwner?.featuredBadges:         │   │   │  │
│  │  │  │   • Map through featured badge IDs      │   │   │  │
│  │  │  │   • Display badge icons (badgeIcons)    │   │   │  │
│  │  │  │   • Show badge name (ID)                │   │   │  │
│  │  │  │   • Hover effects + scale effects       │   │   │  │
│  │  │  │                                           │   │   │  │
│  │  │  │ ELSE:                                    │   │   │  │
│  │  │  │   • Show empty state message            │   │   │  │
│  │  │  └───────────────────────────────────────────┘   │   │  │
│  │  │                                                    │   │  │
│  │  └────────────────────────────────────────────────────┘   │  │
│  │                                                            │  │
│  └──────────────────────────────────────────────────────────┘  │
│           │                                  │                 │
│           │ (HTTP)                           │ (HTTP)          │
│           ▼                                  ▼                 │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │             BACKEND (Spring Boot/Java)                   │  │
│  ├──────────────────────────────────────────────────────────┤  │
│  │                                                          │  │
│  │  ┌────────────────────────────────────────────────────┐ │  │
│  │  │  UserController.java                              │ │  │
│  │  ├────────────────────────────────────────────────────┤ │  │
│  │  │                                                    │ │  │
│  │  │  PUT /api/users/{userId}/profile/featured-badges │ │  │
│  │  │  ├─ Extract badgeId from request body           │ │  │
│  │  │  ├─ Validate:                                    │ │  │
│  │  │  │  ├─ User exists                              │ │  │
│  │  │  │  ├─ badgeId provided                         │ │  │
│  │  │  │  └─ Badge in user.badges (owned)            │ │  │
│  │  │  ├─ Toggle featured status:                     │ │  │
│  │  │  │  ├─ IF badgeId in featuredBadges:           │ │  │
│  │  │  │  │  └─ Remove (unfeature)                   │ │  │
│  │  │  │  └─ ELSE:                                    │ │  │
│  │  │  │     ├─ Check size < 2                       │ │  │
│  │  │  │     └─ Add (feature)                        │ │  │
│  │  │  ├─ Save to MongoDB                             │ │  │
│  │  │  └─ Return updated User object                  │ │  │
│  │  │                                                    │ │  │
│  │  └────────────────────────────────────────────────────┘ │  │
│  │           ▲                                               │  │
│  │           │ userRepository.findById()                    │  │
│  │           │ userRepository.save()                        │  │
│  │           │                                              │  │
│  │  ┌────────┴────────────────────────────────────────────┐ │  │
│  │  │  User.java (Data Model)                            │ │  │
│  │  ├─────────────────────────────────────────────────────┤ │  │
│  │  │                                                     │ │  │
│  │  │  private List<String> badges                       │ │  │
│  │  │  private List<String> displayedBadges             │ │  │
│  │  │  private List<String> featuredBadges ← NEW        │ │  │
│  │  │                                                     │ │  │
│  │  └─────────────────────────────────────────────────────┘ │  │
│  │                                                          │  │
│  └──────────────────────────────────────────────────────────┘  │
│           │                                                     │
│           │ (MongoDB Driver)                                   │
│           ▼                                                     │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │             DATABASE (MongoDB)                           │  │
│  ├──────────────────────────────────────────────────────────┤  │
│  │                                                          │  │
│  │  Collection: users                                      │  │
│  │  ┌─────────────────────────────────────────────────┐   │  │
│  │  │ {                                               │   │  │
│  │  │   "_id": "user-id-123",                        │   │  │
│  │  │   "fullName": "User Name",                     │   │  │
│  │  │   "badges": [                                  │   │  │
│  │  │     "founding-dev",                            │   │  │
│  │  │     "campus-catalyst",                         │   │  │
│  │  │     "pod-pioneer"                              │   │  │
│  │  │   ],                                            │   │  │
│  │  │   "displayedBadges": [...],                    │   │  │
│  │  │   "featuredBadges": [                          │   │  │
│  │  │     "founding-dev"                ← PERSISTED │   │  │
│  │  │   ],                                            │   │  │
│  │  │   ...                                           │   │  │
│  │  │ }                                               │   │  │
│  │  └─────────────────────────────────────────────────┘   │  │
│  │                                                          │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Data Flow Diagram

```
USER ACTION: Click empty slot in BadgeCenter
    │
    ▼
FRONTEND: onClick → setShowFeaturedBadgeModal(true)
    │
    ▼
MODAL RENDERS
├─ Filter: earnedBadges.filter(b => !b.isActive)
├─ Display: Badge icons, names, tiers
└─ Ready for user interaction

USER ACTION: Click badge in modal
    │
    ▼
handleSelectFeaturedBadge(badge) called
    │
    ├─ VALIDATION CHECK 1: badge.isUnlocked?
    │  ├─ YES → Continue
    │  └─ NO → Alert("You can only feature unlocked badges")
    │
    ├─ VALIDATION CHECK 2: getActiveBadges().length >= 2?
    │  ├─ YES → Alert("Maximum 2 badges")
    │  └─ NO → Continue
    │
    ├─ setFeaturedBadgesLoading(true)
    │
    ▼
HTTP REQUEST: PUT /api/users/{userId}/profile/featured-badges
├─ Body: { badgeId: "founding-dev" }
└─ Header: Authorization: Bearer {token}
    │
    ├─────────────────────────────────────────────────────┐
    │                                                     │
    ▼                                                     ▼
FRONTEND (Optimistic)              BACKEND (Processing)
                                   │
├─ badge.isActive = true           ├─ Fetch user
├─ Close modal                      │
├─ Show Featured Badge              ├─ Validate badgeId exists
│                                   │
│                                   ├─ Get featured badges list
│                                   │
│                                   ├─ Check ownership:
│                                   │  user.badges.contains(badgeId)?
│                                   │
│                                   ├─ If already featured:
│                                   │  featuredBadges.remove(badgeId)
│                                   │
│                                   ├─ Else:
│                                   │  Check size < 2?
│                                   │  If yes: featuredBadges.add()
│                                   │  If no: Error response
│                                   │
│                                   ├─ userRepository.save()
│                                   │
    │                               ▼
    │                    HTTP RESPONSE (200 OK)
    │                    {
    │                      "id": "user-id",
    │                      "badges": [...],
    │                      "featuredBadges": ["founding-dev"],
    │                      ...
    │                    }
    │                              │
    └──────────────────────────────┘
                │
                ▼
    FRONTEND: setUser(response.data)
                │
                ├─ Update user state with response
                ├─ Featured badge confirmed in state
                └─ Close modal
                        │
                        ▼
    USER VIEWS PUBLIC PROFILE
                │
                ├─ ProfilePage fetches user
                ├─ Maps featuredBadges array
                ├─ Renders badge icons
                └─ Displays with styling
                        │
                        ▼
    PUBLIC PROFILE SHOWS:
    ⭐ Featured Achievements
    [Badge Icon] [Badge Icon]
     Badge Name   Badge Name
```

---

## Request/Response Flow

### Request (Frontend → Backend)

```
PUT /api/users/user-123/profile/featured-badges HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...

{
  "badgeId": "founding-dev"
}
```

### Response Success (200 OK)

```json
{
  "id": "user-123",
  "fullName": "John Doe",
  "collegeName": "MIT",
  "yearOfStudy": "3rd Year",
  "department": "Computer Science",
  "badges": [
    "founding-dev",
    "campus-catalyst",
    "pod-pioneer"
  ],
  "displayedBadges": ["founding-dev", "campus-catalyst"],
  "featuredBadges": ["founding-dev"],
  "endorsementsCount": 5,
  "level": 3,
  "xp": 250,
  "totalXp": 500,
  "role": "COLLEGE_HEAD",
  "isDev": true,
  "email": "john@example.com",
  "createdAt": "2025-06-01T10:30:00",
  ...
}
```

### Response Error (400 Bad Request)

```json
{
  "error": "You can feature a maximum of 2 badges"
}
```

Or:

```json
{
  "error": "You can only feature badges you have earned"
}
```

---

## State Management Flow

```
┌─────────────────────────────────────────────┐
│     App/Parent Component State              │
├─────────────────────────────────────────────┤
│                                             │
│  user = {                                   │
│    _id: "user-123",                         │
│    badges: [...],                           │
│    featuredBadges: ["founding-dev"],        │
│    ...                                      │
│  }                                          │
│                                             │
│  setUser = (updatedUser) => {...}          │
│                                             │
└────────────────────┬────────────────────────┘
                     │
                     │ Props: user, setUser
                     ▼
         ┌───────────────────────┐
         │  BadgeCenter.jsx       │
         ├───────────────────────┤
         │                       │
         │ Local States:         │
         │ ├─ activeTab          │
         │ ├─ selectedBadge      │
         │ ├─ showFeaturedBadge  │
         │ │  Modal              │
         │ └─ featured Badges    │
         │    Loading            │
         │                       │
         │ Computed:             │
         │ ├─ earnedBadges       │
         │ ├─ allBadges          │
         │ ├─ getActiveBadges()  │
         │ └─ getTierColor()     │
         │                       │
         │ Handlers:             │
         │ └─ handleSelect       │
         │    FeaturedBadge()    │
         │    ├─ PUT request     │
         │    ├─ setUser()       │
         │    └─ close modal     │
         │                       │
         └───────────────────────┘
```

---

## Interaction Sequence Diagram

```
User          Frontend           Backend        Database
 │              │                 │               │
 │ Clicks        │                 │               │
 │ empty slot    │                 │               │
 ├─────────────►│                 │               │
 │              │ Modal renders    │               │
 │              │ (no API call)    │               │
 │              │◄──────────────┐  │               │
 │              │               │  │               │
 │ Selects      │               │  │               │
 │ badge        │               │  │               │
 ├─────────────►│               │  │               │
 │              │               │  │               │
 │              │ Validate       │  │               │
 │              │ frontend       │  │               │
 │              │               │  │               │
 │              │ PUT request    │  │               │
 │              ├──────────────────►│               │
 │              │                │  │ Validate      │
 │              │                │  │ ownership     │
 │              │                │  │ & limit       │
 │              │ (Optimistic)   │  │               │
 │              │ Badge shown    │  │ Check         │
 │              │ immediately    │  │ user.badges   │
 │              │                │  │               │
 │              │                │  │ Toggle        │
 │              │                │  │ featured      │
 │              │                │  │               │
 │              │                │  │ Save to DB    │
 │              │                │  ├──────────────►│
 │              │                │  │◄──────────────┤
 │              │                │  │ Document      │
 │              │                │  │ saved         │
 │              │ Response ◄─────┤ │               │
 │              │ (User data)    │  │               │
 │              │ setUser()      │  │               │
 │              │ Modal closes   │  │               │
 │              │                │  │               │
 │ Sees badge   │                │  │               │
 │ featured     │                │  │               │
 │◄─────────────┤                │  │               │
 │              │                │  │               │
 │ Navigates    │                │  │               │
 │ to profile   │                │  │               │
 ├─────────────►│                │  │               │
 │              │ GET request    │  │               │
 │              ├──────────────────►│               │
 │              │                │  │ Query user    │
 │              │                │  ├──────────────►│
 │              │                │  │◄──────────────┤
 │              │                │  │ Return user   │
 │              │ Response       │  │               │
 │              │ (featuring)◄───┤  │               │
 │              │ Display        │  │               │
 │              │ featured       │  │               │
 │              │ badges         │  │               │
 │              │                │  │               │
 │ Sees badge   │                │  │               │
 │ on profile   │                │  │               │
 │◄─────────────┤                │  │               │
```

---

## Error Handling Flow

```
User Input
    │
    ▼
FRONTEND VALIDATION
├─ Is badge unlocked?
│  ├─ NO → Alert + Return
│  └─ YES → Continue
│
├─ Can feature (< 2)?
│  ├─ NO → Alert + Return
│  └─ YES → Continue
│
▼
HTTP REQUEST TO BACKEND
    │
    ▼
BACKEND VALIDATION
├─ User exists?
│  ├─ NO → 404 Not Found
│  └─ YES → Continue
│
├─ Badge ID provided?
│  ├─ NO → 400 Bad Request
│  └─ YES → Continue
│
├─ User owns badge?
│  ├─ NO → 400 "You can only feature badges you have earned"
│  └─ YES → Continue
│
├─ Adding new badge & at limit?
│  ├─ YES (already has 2) → 400 "You can feature a maximum of 2 badges"
│  └─ NO → Continue
│
▼
SUCCESS
├─ Save to MongoDB
├─ Return 200 + updated user
└─ Frontend receives & updates state

ERROR HANDLING:
├─ Network error → Catch in try-catch
├─ Server error (500) → Generic error message
└─ Show alert to user
```

---

This architecture ensures:

- ✅ Proper separation of concerns
- ✅ Frontend & backend validation (defense in depth)
- ✅ Optimistic UI updates for better UX
- ✅ Data persistence in MongoDB
- ✅ Clean error handling
- ✅ Responsive design
