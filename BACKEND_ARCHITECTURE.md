# Tessera Backend Architecture Documentation

**Target Audience:** Flutter/Mobile Developer Building a Consuming App  
**Framework:** Spring Boot 3.2.5 (Java 17)  
**Database:** MongoDB Atlas  
**Real-Time:** STOMP/WebSocket via Spring Messaging

---

## 1. High-Level Architecture

### Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| **Framework** | Spring Boot Starter | 3.2.5 |
| **Language** | Java | 17 |
| **Database** | MongoDB | Atlas (Cloud) |
| **Authentication** | JWT (JSON Web Tokens) | JJWT 0.11.5 |
| **Real-Time** | Spring WebSocket + STOMP | Built-in |
| **Security** | Spring Security 6.x | Stateless |
| **File Upload** | Multipart FormData | 10MB max |

### Security Model

- **Session:** Stateless (Stateless is set in `SessionCreationPolicy.STATELESS`)
- **JWT Validity:** 5 hours
- **Token Placement:** Both `Authorization: Bearer <token>` header and HTTP cookie (httpOnly)
- **CSRF:** Disabled (stateless APIs)
- **CORS:** Configured for specific origins (see below)

### Project Folder Structure

```
src/main/java/com/studencollabfin/server/
├── ServerApplication.java         # Main Spring Boot entry point (@EnableScheduling)
├── config/                        # Spring configuration beans
│   ├── SecurityConfig.java        # JWT, CORS, session management
│   ├── JwtUtil.java               # JWT generation & validation
│   ├── JwtRequestFilter.java      # JWT servlet filter
│   ├── WebSocketConfig.java       # STOMP endpoints & message broker
│   ├── MongoConfig.java           # MongoDB connection (empty, uses defaults)
│   ├── WebConfig.java             # File upload routing
│   ├── AppConfig.java             # BCryptPasswordEncoder bean
│   ├── GlobalExceptionHandler.java # Exception handling (empty)
│   ├── RequestLoggingFilter.java  # Log file uploads
│   └── collab_d.code-workspace    # Workspace config
├── controller/                    # REST endpoints + WebSocket handlers (19 files)
│   ├── AuthenticationController.java   # POST /auth/login, register, check-email, GET /auth/me
│   ├── AuthController.java             # Empty placeholder
│   ├── UserController.java             # Profile, XP, achievements, badges, endorsements, dev mode
│   ├── PostController.java             # Social posts, polling, likes, filtering
│   ├── CollabPodController.java        # Pod CRUD, messaging, joining, campus/global filtering
│   ├── CommentController.java          # Comment CRUD, threaded replies, filtering
│   ├── BuddyBeaconController.java      # Beacon CRUD, applications, acceptance/rejection
│   ├── EventController.java            # Event CRUD, registration tracking
│   ├── InboxController.java            # Inbox notifications, read status, bulk delete
│   ├── MessagingController.java        # Conversations, DM send, invites
│   ├── ChatWebSocketController.java    # WebSocket chat & typing notifications
│   ├── DiscoveryController.java        # Global skill mesh matching (Jaccard similarity)
│   ├── FileUploadController.java       # File upload to /uploads directory
│   ├── HealthController.java           # Empty placeholder
│   ├── LegacyApplyController.java      # Legacy /api/apply/{beaconId} endpoint
│   ├── ProjectController.java          # Empty placeholder
│   ├── SecurityController.java         # Obsolete OAuth endpoint
│   ├── PodChatWSController.java        # WebSocket pod messaging
│   └── MessagingController.java        # REST messaging endpoints
├── websocket/                     # WebSocket/STOMP controllers (1 file)
│   └── MessagingWebSocketController.java # /app/chat.sendMessage handler
├── model/                         # MongoDB documents (entities, 25 files)
│   ├── User.java                     # User profile, XP, level, badges, roles
│   ├── Post.java                     # Abstract base post (indexed createdAt)
│   ├── SocialPost.java               # Discussion/Ask/Help/Poll/Collab posts
│   ├── TeamFindingPost.java          # Team recruitment posts (24-hour lifecycle)
│   ├── CollabPod.java                # Pods/rooms with role-based members
│   ├── Comment.java                  # Threaded comments with parent references
│   ├── Chat.java                     # 1-to-1 chat sessions (deprecated, use Conversation)
│   ├── Message.java                  # DM & pod chat messages
│   ├── Conversation.java             # Conversation threads (PENDING/ACCEPTED status)
│   ├── BuddyBeacon.java              # Event team recruitment beacons
│   ├── Event.java                    # Events/hackathons with tracking
│   ├── EventReminder.java            # Event reminders (1-day/1-hour/15-min before)
│   ├── Inbox.java                    # Notifications (POD_BAN, APPLICATION_REJECTION, etc)
│   ├── Application.java              # Application to beacons (PENDING/ACCEPTED/REJECTED)
│   ├── Achievement.java              # Badges/achievements with unlock conditions
│   ├── Project.java                  # Projects with milestones & tasks
│   ├── PodMessage.java               # Pod chat messages (TTL 3 days)
│   ├── PodCooldown.java              # Rate-limit pod leave/rejoin (TTL 15 min)
│   ├── PodScope.java                 # Enum: CAMPUS, GLOBAL
│   ├── PostType.java                 # Enum: DISCUSSION, COLLAB, ASK_HELP, etc
│   ├── PostState.java                # Enum: ACTIVE, CLOSED, EXPIRED
│   ├── RejectionReason.java          # Enum: NOT_A_GOOD_FIT, TEAM_FULL, OTHER
│   ├── PollOption.java               # Poll vote tracking
│   └── XPAction.java                 # Enum: CREATE_POST, CREATE_EVENT, etc
├── repository/                    # MongoDB data access (Spring Data, 11 files)
│   ├── UserRepository.java           # CRUD + custom queries
│   ├── PostRepository.java           # CRUD + filter by college/type
│   ├── CollabPodRepository.java      # CRUD + scope/college queries
│   ├── CommentRepository.java        # CRUD + parent/thread queries
│   ├── ChatRepository.java           # CRUD + sender/receiver queries
│   ├── ConversationRepository.java   # CRUD + participant queries
│   ├── MessageRepository.java        # CRUD + conversation/pod queries
│   ├── BuddyBeaconRepository.java    # CRUD + college/status queries
│   ├── EventRepository.java          # CRUD + category/date queries
│   ├── InboxRepository.java          # CRUD + user/read/type queries
│   ├── ApplicationRepository.java    # CRUD + beacon/status queries
│   ├── AchievementRepository.java    # CRUD + user queries
│   ├── ProjectRepository.java        # CRUD + leader/member queries
│   ├── PodMessageRepository.java     # CRUD + pod/timestamp queries
│   ├── PodCooldownRepository.java    # CRUD (auto-deletes after TTL)
│   └── EventReminderRepository.java  # CRUD + due reminder queries
├── service/                       # Business logic (13 files)
│   ├── UserService.java             # User CRUD, auth, profile, XP
│   ├── PostService.java             # Post CRUD, likes, voting, comments
│   ├── CollabPodService.java        # Pod CRUD, messaging, member management
│   ├── ChatService.java             # 1-to-1 chat (deprecated)
│   ├── CommentService.java          # Comment CRUD, threading, cascading delete
│   ├── EventService.java            # Event CRUD, registration, stats refresh
│   ├── AchievementService.java      # Badge unlocking, syncing
│   ├── GamificationService.java     # XP rewards, level calculation
│   ├── MessagingService.java        # Conversation, DM, invites
│   ├── BuddyBeaconService.java      # Beacon CRUD, applications
│   ├── SkillSimilarityService.java  # Jaccard similarity matching
│   ├── NotificationService.java     # WebSocket broadcasts to topics/queues
│   ├── CleanupService.java          # Scheduled: delete posts > 24h
│   ├── TeamCleanupService.java      # Scheduled: convert TeamFindingPost to Pods
│   ├── ReminderService.java         # Scheduled: send event reminders
│   ├── PodMessageService.java       # Pod message CRUD + cascade delete
│   └── ProjectService.java          # Project CRUD, milestones, tasks
├── dto/                           # Request/Response DTOs (8 files)
│   ├── AuthenticationRequest.java    # { email, password }
│   ├── AuthenticationResponse.java   # { token, userId, collegeName, badges }
│   ├── RegisterRequest.java          # { email, password }
│   ├── CommentRequest.java           # { content, parentId }
│   ├── CreateEventRequest.java       # Event creation payload
│   ├── ProfileUpdateRequest.java     # Profile update payload
│   ├── UpdateProfileRequest.java     # Alternative profile update
│   └── ApplyRequest.java             # { message } for beacon application
└── exception/                     # Custom exceptions (3 files)
    ├── PermissionDeniedException.java # 403 Forbidden
    ├── CooldownException.java         # 429 Too Many Requests
    └── BannedFromPodException.java    # 403 Pod ban
```

---

## 2. Authentication & Security Flow

### JWT Authentication Workflow

1. **Registration** → `/api/auth/register`
   - User provides email + password
   - Password hashed (bcrypt assumed)
   - User stored in MongoDB
   
2. **Login** → `/api/auth/login`
   - User provides email + password
   - Backend validates credentials
   - JWT generated (5-hour validity)
   - Token returned in response + set as httpOnly cookie

3. **Token Validation**
   - Each protected request must include token
   - Token extracted from `Authorization: Bearer <token>` header OR cookie
   - `JwtUtil.validateToken()` verifies signature and expiration
   - If valid, request proceeds; if invalid, returns **401 Unauthorized**

### Required Headers for Protected Endpoints

| Header | Purpose | Example |
|--------|---------|---------|
| `Authorization` | JWT token (Bearer scheme) | `Bearer eyJhbGc...` |
| `X-User-Id` | Alternative user ID (fallback) | `user-uuid-12345` |
| `Content-Type` | For POST/PUT payloads | `application/json` |

### CORS Configuration

**Allowed Origins:**
```
- http://localhost:5173     (React dev server)
- http://localhost:5174     (React alternate)
- http://localhost:3000     (React alternate)
- https://tezzera.netlify.app  (Production frontend)
```

**Allowed Methods:** GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD  
**Allowed Headers:** `*` (all)  
**Exposed Headers:** `*` (all)  
**Credentials:** `true` (allows cookies)

### Security Features Implemented

✅ JWT validation on all endpoints  
✅ CSRF disabled (stateless)  
✅ CORS whitelist configured  
✅ SessionCreationPolicy.STATELESS (no session storage)  
✅ Password field marked as WRITE_ONLY (never returned in responses)  
✅ Campus isolation (posts/pods filtered by college)  
✅ Role-based authorization (STUDENT, COLLEGE_HEAD, DEV)  

---

## 3. API Endpoints & Data Models

### 3.1 Authentication Endpoints

#### POST `/api/auth/register`
Register a new user account.

**Request:**
```json
{
  "email": "student@example.com",
  "password": "securePass123"
}
```

**Response (200 OK):**
```json
{
  "id": "user_uuid_12345",
  "email": "student@example.com",
  "message": "User registered successfully"
}
```

---

#### POST `/api/auth/login`
Authenticate and receive JWT token.

**Request:**
```json
{
  "email": "student@example.com",
  "password": "securePass123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": "user_uuid_12345",
  "email": "student@example.com",
  "fullName": "John Doe",
  "profileCompleted": true,
  "collegeName": "SINHGAD",
  "badges": ["Signal Guardian", "Skill Sage"]
}
```

**Cookie Set:** `token=<JWT>` (httpOnly, 7-day expiry)

---

#### POST `/api/auth/check-email`
Verify if email exists in database.

**Request:**
```json
{
  "email": "student@example.com"
}
```

**Response (200 OK):**
```json
{
  "exists": true
}
```

---

#### GET `/api/auth/me`
Get current authenticated user's profile.

**Headers Required:** `Authorization: Bearer <token>`

**Response (200 OK):**
```json
{
  "id": "user_uuid_12345",
  "fullName": "John Doe",
  "email": "john@example.com",
  "collegeName": "SINHGAD",
  "yearOfStudy": "3rd Year",
  "department": "Electronics",
  "level": 5,
  "xp": 450,
  "totalXp": 2500,
  "badges": ["Signal Guardian"],
  "profileCompleted": true
}
```

---

### 3.2 User Profile Endpoints

#### GET `/api/users/{userId}`
Fetch user profile (public view with badges synced).

**Response (200 OK):**
```json
{
  "id": "user_uuid_12345",
  "fullName": "John Doe",
  "collegeName": "SINHGAD",
  "yearOfStudy": "3rd Year",
  "department": "Electronics",
  "skills": ["Flutter", "UI/UX Design", "Firebase"],
  "goals": "Build innovative mobile apps",
  "badges": ["Signal Guardian", "Skill Sage"],
  "displayedBadges": ["Signal Guardian"],
  "level": 5,
  "xp": 450,
  "endoresementsCount": 12,
  "postsCount": 8,
  "profilePicUrl": "https://...",
  "linkedinUrl": "https://linkedin.com/in/johndoe",
  "githubUrl": "https://github.com/johndoe",
  "createdAt": "2025-12-01T10:30:00"
}
```

---

#### PUT `/api/users/{userId}`
Update user profile.

**Request:**
```json
{
  "fullName": "John Doe Updated",
  "yearOfStudy": "3rd Year",
  "department": "Electronics",
  "skills": ["Flutter", "Firebase"],
  "goals": "Build innovative apps",
  "linkedinUrl": "https://linkedin.com/in/johndoe",
  "githubUrl": "https://github.com/johndoe"
}
```

**Response (200 OK):** Updated User object with synced badges

---

#### GET `/api/users/{userId}/xp`
Get user's XP progress towards next level.

**Response (200 OK):**
```json
{
  "currentXP": 45,
  "level": 5,
  "nextLevelXP": 55
}
```

---

#### GET `/api/users/{userId}/achievements`
Get list of all achievements/badges earned by user.

**Response (200 OK):**
```json
[
  {
    "id": "achievement_1",
    "name": "Signal Guardian",
    "description": "Create your first post",
    "icon": "https://...",
    "unlockedAt": "2025-12-01T14:22:00"
  },
  {
    "id": "achievement_2",
    "name": "Skill Sage",
    "description": "Receive 5 endorsements",
    "icon": "https://...",
    "unlockedAt": "2025-12-15T09:10:00"
  }
]
```

---

#### POST `/api/users/{userId}/endorse`
Endorse a user's skill (for Skill Sage achievement).

**Request:**
```json
{
  "endorserId": "endorser_user_id",
  "skill": "Flutter"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Skill endorsed",
  "endorsementsCount": 13
}
```

---

### 3.3 Posts Endpoints

#### GET `/api/posts`
Get all posts for the current user's college (campus isolation).

**Query Parameters:**
- `type` (optional): Filter by PostType (DISCUSSION, ASK, HELP, LOOKING_FOR, COLLAB)

**Response (200 OK):**
```json
[
  {
    "id": "post_uuid_1",
    "authorId": "user_uuid_123",
    "authorName": "John Doe",
    "authorCollege": "SINHGAD",
    "authorYear": "3rd Year",
    "title": "Need help with Flutter layouts",
    "content": "I'm struggling with complex layouts...",
    "type": "ASK",
    "postType": "ASK",
    "createdAt": "2025-12-20T10:30:00",
    "likes": ["user_uuid_456", "user_uuid_789"],
    "commentIds": ["comment_1", "comment_2"],
    "requiredSkills": ["Flutter", "Dart"],
    "linkedPodId": "pod_123"
  }
]
```

---

#### POST `/api/posts/social`
Create a new social post (Discussion/Ask/Help).

**Request:**
```json
{
  "title": "Need help with Flutter layouts",
  "content": "I'm struggling with complex layouts...",
  "type": "ASK",
  "requiredSkills": ["Flutter", "Dart"],
  "linkedPodId": "pod_123"
}
```

**Response (201 Created):**
```json
{
  "id": "post_uuid_1",
  "authorId": "current_user_id",
  "title": "Need help with Flutter layouts",
  "type": "ASK",
  "createdAt": "2025-12-20T10:30:00",
  "likes": [],
  "commentIds": []
}
```

---

#### PUT `/api/posts/{postId}/like`
Toggle like/unlike on a post.

**Response (200 OK):**
```json
{
  "id": "post_uuid_1",
  "likes": ["user_uuid_123", "user_uuid_456"],
  "likesCount": 2
}
```

---

#### PUT `/api/posts/{postId}/vote/{optionId}`
Vote on a poll option in a post.

**Response (200 OK):**
```json
{
  "id": "post_uuid_1",
  "pollOptions": [
    {
      "id": "option_1",
      "text": "Flutter",
      "votes": ["user_uuid_123"]
    }
  ]
}
```

---

### 3.4 Comments Endpoints

#### GET `/api/comments/post/{postId}`
Get top-level comments for a post (paginated).

**Response (200 OK):**
```json
[
  {
    "id": "comment_1",
    "postId": "post_uuid_1",
    "authorId": "user_uuid_456",
    "authorName": "Jane Smith",
    "content": "Try using Flexible widget",
    "createdAt": "2025-12-20T11:15:00",
    "replyIds": ["comment_2"]
  }
]
```

---

#### GET `/api/comments/replies/{postId}/{parentId}`
Get reply comments for a parent comment.

**Response (200 OK):**
```json
[
  {
    "id": "comment_2",
    "parentId": "comment_1",
    "authorId": "user_uuid_123",
    "authorName": "John Doe",
    "content": "Thanks, that worked!",
    "createdAt": "2025-12-20T11:45:00"
  }
]
```

---

#### POST `/api/comments`
Create a new comment on a post.

**Request:**
```json
{
  "postId": "post_uuid_1",
  "content": "Try using Flexible widget",
  "parentId": null
}
```

**Response (201 Created):**
```json
{
  "id": "comment_1",
  "postId": "post_uuid_1",
  "authorId": "current_user_id",
  "content": "Try using Flexible widget",
  "createdAt": "2025-12-20T11:15:00",
  "replyIds": []
}
```

---

#### DELETE `/api/comments/{commentId}`
Delete a comment (author or moderator only).

**Response (204 No Content)**

---

### 3.5 Collab Pods Endpoints

#### GET `/pods`
Get all collaboration pods (optionally filtered by scope).

**Query Parameters:**
- `scope` (optional): CAMPUS or GLOBAL

**Response (200 OK):**
```json
[
  {
    "id": "pod_uuid_1",
    "name": "Flutter Study Group",
    "description": "Learn Flutter together",
    "ownerId": "user_uuid_123",
    "ownerName": "John Doe",
    "memberIds": ["user_uuid_123", "user_uuid_456"],
    "memberNames": ["John Doe", "Jane Smith"],
    "memberCount": 2,
    "adminIds": ["user_uuid_123"],
    "topics": ["Flutter", "Dart"],
    "type": "DISCUSSION",
    "scope": "CAMPUS",
    "college": "SINHGAD",
    "status": "ACTIVE",
    "createdAt": "2025-12-01T14:30:00"
  }
]
```

---

#### GET `/pods/campus`
Get all CAMPUS-scoped pods for current user's college (campus isolation).

**Response (200 OK):** Same as above, filtered to CAMPUS scope

---

#### GET `/pods/global`
Get all GLOBAL-scoped pods (cross-college).

**Response (200 OK):** Same as above, filtered to GLOBAL scope

---

#### GET `/pods/{id}`
Get a specific pod by ID.

**Response (200 OK):** Single pod object

---

#### POST `/pods`
Create a new collaboration pod.

**Request:**
```json
{
  "name": "Flutter Study Group",
  "description": "Learn Flutter together",
  "ownerId": "current_user_id",
  "ownerName": "John Doe",
  "topics": ["Flutter", "Dart"],
  "type": "DISCUSSION",
  "scope": "CAMPUS",
  "college": "SINHGAD",
  "maxCapacity": 20
}
```

**Response (201 Created):** Created pod object

---

#### POST `/pods/{id}/join`
Add user to pod members list.

**Request:**
```json
{
  "userId": "user_uuid_456"
}
```

**Response (200 OK):**
```json
{
  "id": "pod_uuid_1",
  "memberIds": ["user_uuid_123", "user_uuid_456"],
  "memberCount": 2
}
```

---

#### POST `/pods/beacon/apply/{id}`
Apply to a pod via Buddy Beacon (team formation).

**Request:**
```json
{
  "userId": "user_uuid_456"
}
```

**Response (200 OK):** Updated pod with applicants list

---

#### GET `/pods/{id}/messages`
Get all messages in a pod's chat.

**Response (200 OK):**
```json
[
  {
    "id": "msg_1",
    "podId": "pod_uuid_1",
    "senderId": "user_uuid_123",
    "senderName": "John Doe",
    "content": "Hello team!",
    "sentAt": "2025-12-20T10:00:00",
    "attachmentUrls": []
  }
]
```

---

#### POST `/pods/{id}/messages`
Send a message to pod chat (REST endpoint, but WebSocket preferred).

**Request:**
```json
{
  "senderId": "user_uuid_123",
  "senderName": "John Doe",
  "content": "Hello team!",
  "attachmentUrls": []
}
```

**Response (200 OK):** Saved message object

---

### 3.6 Buddy Beacon Endpoints

#### POST `/api/beacon`
Create a new Buddy Beacon post (team recruitment for events).

**Request:**
```json
{
  "eventId": "event_uuid_1",
  "eventName": "TechFest 2026",
  "title": "Need experienced developer for hackathon",
  "description": "Looking for a senior Flutter dev...",
  "requiredSkills": ["Flutter", "Firebase"],
  "maxTeamSize": 4
}
```

**Response (201 Created):**
```json
{
  "id": "beacon_uuid_1",
  "authorId": "current_user_id",
  "eventId": "event_uuid_1",
  "title": "Need experienced developer for hackathon",
  "status": "ACTIVE",
  "currentTeamMemberIds": ["user_uuid_123"],
  "applicants": [],
  "createdAt": "2025-12-20T10:30:00"
}
```

---

#### GET `/api/beacon/feed`
Get all Buddy Beacon posts for current user's college.

**Response (200 OK):**
```json
[
  {
    "id": "beacon_uuid_1",
    "authorId": "user_uuid_123",
    "authorName": "John Doe",
    "eventName": "TechFest 2026",
    "title": "Need experienced developer",
    "requiredSkills": ["Flutter"],
    "maxTeamSize": 4,
    "currentTeamMembers": 2,
    "applicants": ["user_uuid_789"],
    "createdAt": "2025-12-20T10:30:00"
  }
]
```

---

#### POST `/api/beacon/apply/{beaconId}`
Apply to a Buddy Beacon post.

**Request:**
```json
{
  "message": "I have 3 years of Flutter experience"
}
```

**Response (200 OK):**
```json
{
  "id": "application_uuid_1",
  "beaconId": "beacon_uuid_1",
  "applicantId": "current_user_id",
  "status": "PENDING",
  "createdAt": "2025-12-20T11:00:00"
}
```

---

#### POST `/api/beacon/application/{applicationId}/accept`
Accept an application (beacon author only).

**Query Parameters:**
- `postId`: The beacon ID

**Response (200 OK):**
```json
{
  "id": "application_uuid_1",
  "status": "ACCEPTED",
  "acceptedAt": "2025-12-20T11:30:00"
}
```

---

#### GET `/api/beacon/applied-posts`
Get posts the user has applied to.

**Response (200 OK):**
```json
[
  {
    "beaconId": "beacon_uuid_1",
    "eventName": "TechFest 2026",
    "applicationStatus": "PENDING",
    "appliedAt": "2025-12-20T11:00:00"
  }
]
```

---

#### GET `/api/beacon/my-posts`
Get posts created by the current user.

**Response (200 OK):**
```json
[
  {
    "beaconId": "beacon_uuid_1",
    "title": "Need experienced developer",
    "applicants": [
      {
        "applicantId": "user_uuid_456",
        "name": "Jane Smith",
        "skills": ["Flutter"],
        "status": "PENDING"
      }
    ]
  }
]
```

---

### 3.7 Events Endpoints

#### GET `/api/events`
Get all events (optionally filtered by category).

**Query Parameters:**
- `category` (optional): HACKATHON, WORKSHOP, TECH_TALK, STUDY_GROUP, etc.

**Response (200 OK):**
```json
[
  {
    "id": "event_uuid_1",
    "title": "TechFest 2026 Hackathon",
    "description": "72-hour hackathon for students",
    "category": "HACKATHON",
    "organizer": "user_uuid_123",
    "startDate": "2026-01-15T09:00:00",
    "linkEndDate": "2026-01-10T23:59:59",
    "type": "HACKATHON",
    "status": "UPCOMING",
    "location": "Virtual",
    "meetingLink": "https://meet.google.com/...",
    "registrationLink": "https://forms.google.com/...",
    "maxParticipants": 500,
    "currentParticipants": 150,
    "requiredSkills": ["Problem Solving"],
    "tags": ["Web", "Mobile", "AI"],
    "hasRegistered": false
  }
]
```

---

#### GET `/api/events/{id}`
Get a specific event by ID.

**Response (200 OK):** Single event object

---

#### POST `/api/events`
Create a new event (requires authorization).

**Request:**
```json
{
  "title": "TechFest 2026 Hackathon",
  "description": "72-hour hackathon",
  "category": "HACKATHON",
  "type": "HACKATHON",
  "startDate": "2026-01-15T09:00:00",
  "linkEndDate": "2026-01-10T23:59:59",
  "location": "Virtual",
  "meetingLink": "https://meet.google.com/...",
  "registrationLink": "https://forms.google.com/...",
  "maxParticipants": 500,
  "requiredSkills": ["Problem Solving"]
}
```

**Response (201 Created):** Created event object

---

#### POST `/api/events/{id}/register-click`
Track unique user registration click for event.

**Headers Required:** `X-User-Id: <user_id>`

**Response (200 OK):**
```json
{
  "id": "event_uuid_1",
  "title": "TechFest 2026 Hackathon",
  "currentParticipants": 151
}
```

---

#### DELETE `/api/events/{id}`
Delete an event (requires authorization).

**Response (204 No Content)**

---

### 3.8 Inbox / Notifications Endpoints

#### GET `/api/inbox/my`
Get all inbox notifications for current user.

**Query Parameters:**
- `userId` (required): Current user ID

**Response (200 OK):**
```json
[
  {
    "id": "inbox_1",
    "userId": "current_user_id",
    "type": "POD_BAN",
    "title": "Removed from Pod",
    "message": "You were removed from 'Flutter Study Group'",
    "podId": "pod_uuid_1",
    "podName": "Flutter Study Group",
    "reason": "Inactive for 30 days",
    "severity": "HIGH",
    "createdAt": "2025-12-20T10:30:00",
    "read": false
  },
  {
    "id": "inbox_2",
    "userId": "current_user_id",
    "type": "APPLICATION_REJECTION",
    "title": "Application Rejected",
    "message": "Your application was not accepted",
    "postId": "beacon_uuid_1",
    "postTitle": "Hackathon Team",
    "rejectionReason": "Team full",
    "severity": "MEDIUM",
    "createdAt": "2025-12-19T14:15:00",
    "read": false
  }
]
```

---

#### GET `/api/inbox/my/unread`
Get only unread inbox items.

**Query Parameters:**
- `userId` (required): Current user ID

**Response (200 OK):** Same as above, filtered to unread only

---

#### PATCH `/api/inbox/{id}/read`
Mark an inbox item as read.

**Response (200 OK):**
```json
{
  "id": "inbox_1",
  "read": true,
  "updatedAt": "2025-12-20T11:00:00"
}
```

---

#### DELETE `/api/inbox/{id}`
Delete an inbox notification.

**Response (204 No Content)**

---

### 3.9 Messaging / Direct Messages Endpoints

#### GET `/api/messages/conversations/{userId}`
Get all conversations for a user.

**Response (200 OK):**
```json
[
  {
    "id": "conv_uuid_1",
    "participantIds": ["user_uuid_123", "user_uuid_456"],
    "participantNames": ["John Doe", "Jane Smith"],
    "status": "ACTIVE",
    "lastMessage": "Thanks for the help!",
    "lastMessageTime": "2025-12-20T15:30:00",
    "unreadCount": 2
  }
]
```

---

#### POST `/api/messages/conversations`
Create a new conversation.

**Request:**
```json
{
  "participantIds": ["user_uuid_123", "user_uuid_456"]
}
```

**Response (201 Created):** Created conversation object

---

#### GET `/api/messages/conversation/{conversationId}/messages`
Get all messages in a conversation.

**Response (200 OK):**
```json
[
  {
    "id": "msg_1",
    "conversationId": "conv_uuid_1",
    "senderId": "user_uuid_123",
    "senderName": "John Doe",
    "text": "Hey, how are you?",
    "attachmentUrls": [],
    "sentAt": "2025-12-20T10:00:00",
    "read": true
  }
]
```

---

#### POST `/api/messages/conversation/{conversationId}/send`
Send a message in a conversation.

**Request:**
```json
{
  "senderId": "user_uuid_123",
  "text": "Hey, how are you?",
  "attachmentUrls": []
}
```

**Response (200 OK):** Saved message object

---

#### POST `/api/messages/invite/{targetId}`
Send a collaboration invite to another user.

**Request:**
```json
{
  "senderId": "user_uuid_123"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Invite sent successfully",
  "conversationId": "conv_uuid_1"
}
```

---

#### GET `/api/messages/invites/pending/{userId}`
Get all pending collaboration invites for a user.

**Response (200 OK):**
```json
[
  {
    "id": "conv_uuid_1",
    "senderId": "user_uuid_123",
    "senderName": "John Doe",
    "status": "PENDING",
    "sentAt": "2025-12-20T11:00:00"
  }
]
```

---

#### POST `/api/messages/invite/{conversationId}/respond`
Accept or reject a collaboration invite.

**Request:**
```json
{
  "accept": true
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Invite accepted",
  "conversationId": "conv_uuid_1"
}
```

---

### 3.10 Discovery Endpoints

#### GET `/api/discovery/mesh`
Get top 5 global skill matches for current user (Jaccard similarity).

**Headers Required:** User must be authenticated

**Response (200 OK):**
```json
[
  {
    "id": "user_uuid_456",
    "fullName": "Jane Smith",
    "collegeName": "IIT",
    "skills": ["Flutter", "Firebase", "Problem Solving"],
    "level": 7,
    "badges": ["Signal Guardian"]
  },
  {
    "id": "user_uuid_789",
    "fullName": "Bob Johnson",
    "collegeName": "DJSCE",
    "skills": ["Flutter", "Dart"],
    "level": 4,
    "badges": []
  }
]
```

---

### 3.11 File Upload Endpoints

#### POST `/api/uploads/pod-files`
Upload a file to pod (image or document).

**Request (multipart/form-data):**
- `file`: The file to upload (max 10MB)

**Response (200 OK):**
```json
{
  "success": true,
  "fileUrl": "https://backend.com/uploads/uuid-12345.pdf",
  "fileName": "document.pdf",
  "attachmentType": "FILE"
}
```

---

### 3.12 Application Model

The `Application` model represents applications/requests to join teams (BeaconPosts, TeamFindingPosts).

**Structure:**
```json
{
  "id": "app_uuid_1",
  "beaconId": "beacon_uuid_1",
  "applicantId": "user_uuid_456",
  "applicantSkills": ["Flutter", "Firebase"],
  "message": "I have 3 years of Flutter experience",
  "status": "PENDING",
  "rejectionReason": "TEAM_FULL",
  "rejectionNote": "We found enough members",
  "createdAt": "2025-12-20T11:00:00"
}
```

**Status Enum:**
- PENDING
- ACCEPTED
- REJECTED

**RejectionReason Enum:**
- NOT_A_GOOD_FIT
- TEAM_FULL
- OTHER

---

### 3.13 Event Reminder Model

**Structure:**
```json
{
  "id": "reminder_1",
  "eventId": "event_uuid_1",
  "userId": "user_uuid_123",
  "reminderTime": "2026-01-14T09:00:00",
  "type": "ONE_DAY_BEFORE",
  "sent": false
}
```

**ReminderType Enum:**
- ONE_DAY_BEFORE
- ONE_HOUR_BEFORE
- FIFTEEN_MINUTES_BEFORE
- CUSTOM

---

### 3.14 Pod Cooldown Model

Implements rate-limiting for pod leave/rejoin operations.

**Structure:**
```json
{
  "id": "cooldown_1",
  "userId": "user_uuid_123",
  "podId": "pod_uuid_1",
  "action": "LEAVE",
  "createdAt": "2025-12-20T10:00:00",
  "expiryDate": "2025-12-20T10:15:00"
}
```

**Features:**
- TTL Index auto-deletes records after 15 minutes
- Prevents spam leave/rejoin within cooldown period
- Tracks action type (LEAVE, REJOIN, KICK)

---

### 3.15 PollOption Model

Embedded in `SocialPost` for voting functionality.

**Structure:**
```json
{
  "id": "option_uuid_1",
  "text": "Flutter",
  "votes": ["user_uuid_123", "user_uuid_456"]
}
```

---

### 3.16 PostType Enum

Defines types of social posts:

```java
public enum PostType {
  GENERAL("Post"),
  DISCUSSION("Discussion"),
  COLLAB("Collab"),
  ASK_HELP("Ask for Help"),
  OFFER_HELP("Offer Help"),
  POLL("Create Poll"),
  LOOKING_FOR("Looking For...")
}
```

---

### 3.17 PostState Enum

Lifecycle states for posts:

```java
public enum PostState {
  ACTIVE,      // Currently accepting participation
  CLOSED,      // Manually closed by author
  EXPIRED      // Auto-expired after 24 hours (TeamFindingPost)
}
```

---

### 3.18 PodScope Enum

Determines visibility of collaboration pods:

```java
public enum PodScope {
  CAMPUS,      // Visible only within user's college
  GLOBAL       // Visible to all users across colleges
}
```

---

### 3.19 Project Model

Projects with milestones and task management.

**Structure:**
```json
{
  "id": "project_1",
  "name": "Mobile App MVP",
  "description": "Build iOS/Android app",
  "leaderId": "user_123",
  "memberIds": ["user_123", "user_456"],
  "maxTeamSize": 5,
  "status": "IN_PROGRESS",
  "startDate": "2025-12-01T00:00:00",
  "endDate": "2026-01-31T00:00:00",
  "githubLink": "https://github.com/...",
  "tags": ["Mobile", "MVP"],
  "milestones": [
    {
      "id": "milestone_1",
      "title": "UI/UX Design",
      "dueDate": "2025-12-15T00:00:00",
      "completed": false,
      "tasks": [
        {
          "id": "task_1",
          "description": "Create wireframes",
          "assignedTo": "user_456",
          "completed": false,
          "dueDate": "2025-12-10T00:00:00"
        }
      ]
    }
  ]
}
```

**ProjectStatus Enum:**
- PLANNING
- IN_PROGRESS
- COMPLETED
- ON_HOLD
- CANCELLED

---

### 3.20 Entity Relationships (Complete Diagram)

```
User (1) ─────→ (N) Post/SocialPost/TeamFindingPost
  ↓                    ↓
  ├─→ (N) Comment      ├─→ (N) Comment
  ├─→ (N) CollabPod    ├─→ (N) Likes
  ├─→ (N) Achievement  ├─→ (N) PollOptions
  ├─→ (N) BuddyBeacon  ├─→ (N) Application
  ├─→ (N) Inbox        └─→ (N) PodCooldown
  ├─→ (N) Chat/Message
  ├─→ (N) Conversation
  ├─→ (N) Project (as leader/member)
  └─→ (N) EventReminder

Event (1) ─────→ (N) BuddyBeacon
  ├─→ (N) EventReminder (per user)
  └─→ (N) CollabPod (created from expired TeamFindingPost)

BuddyBeacon (1) ───→ (N) Application
                     └─→ (N) User (applicants)

CollabPod (1) ──→ (N) PodMessage (TTL 3 days)
             ├──→ (N) PodCooldown (TTL 15 min)
             ├──→ (N) User (members/admins/owner)
             └──→ (N) Message

Message (1) ──→ (1) User (sender)

Chat (1) ───→ (N) Message
     └─────→ (2) User (sender/receiver)

Conversation (1) → (N) Message
              └──→ (N) User (participants)

Project (1) ──→ (N) Milestone
          ├──→ (N) User (members)
          └──→ (N) Task
                └──→ (1) User (assignee)

Comment (1) ──→ (1) Post
          ├──→ (1) User (author)
          ├──→ (N) Comment (replies, parentId=this.id)
          └──→ (1) Comment (parent, if reply)
```

---

## 4. Real-Time Features (WebSocket/STOMP)

### WebSocket Connection Setup

**Web Client Endpoint:** `/ws-studcollab`  
**Mobile Client Endpoint:** `/ws-studcollab-mobile`  
**Protocol:** STOMP over SockJS (web) / Raw WebSocket (mobile)  
**Broker Prefixes:** `/topic`, `/queue`, `/user`  
**App Destination Prefix:** `/app`

**Connection URLs:**
- Web: `wss://tessera-backend-spx3.onrender.com/ws-studcollab`
- Mobile: `wss://tessera-backend-spx3.onrender.com/ws-studcollab-mobile`

**Mobile clients should connect to:** `wss://tessera-backend-spx3.onrender.com/ws-studcollab-mobile`

### Connection Workflow (Flutter/Client)

```javascript
// Pseudo-code for client connection
const stompClient = new StompClient(url: 'http://backend.com/ws-studcollab');
stompClient.activate();
```

### 4.1 Pod Chat (Real-Time)

**Send Message:**
- **Destination:** `/app/pod.{podId}.chat`
- **Payload:**
  ```json
  {
    "podId": "pod_uuid_1",
    "senderId": "user_uuid_123",
    "senderName": "John Doe",
    "content": "Hello team!",
    "attachmentUrls": []
  }
  ```

**Subscribe to Pod Chat:**
- **Topic:** `/topic/pod.{podId}.chat`
- **Message Received:**
  ```json
  {
    "id": "msg_1",
    "podId": "pod_uuid_1",
    "senderId": "user_uuid_123",
    "senderName": "John Doe",
    "content": "Hello team!",
    "sentAt": "2025-12-20T10:00:00"
  }
  ```

---

### 4.2 Post Comments (Real-Time)

**Send Comment:**
- **Destination:** `/app/post.{postId}.comment`
- **Payload:**
  ```json
  {
    "postId": "post_uuid_1",
    "authorId": "user_uuid_123",
    "content": "Great post!",
    "parentId": null
  }
  ```

**Subscribe to Post Comments:**
- **Topic:** `/topic/post.{postId}.comments`
- **Message Received:**
  ```json
  {
    "comment": {
      "id": "comment_1",
      "postId": "post_uuid_1",
      "authorId": "user_uuid_123",
      "content": "Great post!",
      "createdAt": "2025-12-20T11:30:00"
    },
    "parentId": null
  }
  ```

---

### 4.3 Direct Messages (Real-Time)

**Send DM:**
- **Destination:** `/app/chat.sendMessage`
- **Payload:**
  ```json
  {
    "conversationId": "conv_uuid_1",
    "senderId": "user_uuid_123",
    "text": "Hey, how are you?",
    "attachmentUrls": []
  }
  ```

**Subscribe to Conversation:**
- **Topic:** `/topic/conversation.{conversationId}`
- **Message Received:**
  ```json
  {
    "id": "msg_1",
    "conversationId": "conv_uuid_1",
    "senderId": "user_uuid_123",
    "text": "Hey, how are you?",
    "timestamp": "2025-12-20T10:00:00"
  }
  ```

**Subscribe to User-Specific Queue (1-to-1):**
- **Queue:** `/queue/user/{userId}/messages`
- Receives direct notifications targeted to this user

---

### 4.4 Chat Typing Indicator

**Send Typing Notification:**
- **Destination:** `/app/chat.typing`
- **Payload:**
  ```json
  {
    "senderId": "user_uuid_123",
    "receiverId": "user_uuid_456",
    "isTyping": true
  }
  ```

**Subscribe to Typing Notifications:**
- **Queue:** `/queue/user/{receiverId}/typing`
- **Message Received:**
  ```json
  {
    "senderId": "user_uuid_123",
    "isTyping": true
  }
  ```

---

### 4.5 User Notifications via WebSocket

**Subscribe to User Notifications:**
- **Queue:** `/user/{userId}/queue/notifications`
- **Possible Messages:**
  - Pod ban notification
  - Application rejection
  - New message from contact
  - Event update

---

### WebSocket Topic Summary

| Topic/Queue | Purpose | Subscribers |
|------------|---------|-------------|
| `/topic/pod.{podId}.chat` | Pod chat messages | All pod members |
| `/topic/post.{postId}.comments` | Post comment notifications | Post author + subscribers |
| `/topic/conversation.{convId}` | Group conversation messages | All participants |
| `/queue/user/{userId}/messages` | User-specific 1-to-1 DMs | Specific user |
| `/queue/user/{userId}/typing` | Typing indicators | Specific user |
| `/user/{userId}/queue/notifications` | Inbox notifications | Specific user |

---

## 5. Additional Service Layer Features

### 5.1 Skill Matching Service (SkillSimilarityService)

**Purpose:** Jaccard similarity calculation for global user discovery.

**Algorithm:**
```
Jaccard Similarity = |Intersection of Skills| / |Union of Skills|
Range: 0.0 (no overlap) to 1.0 (identical)
```

**Usage:**
- Used by `GET /api/discovery/mesh` to rank top 5 global matches
- Case-insensitive skill comparison
- Handles null/empty skill lists gracefully

**Example Calculation:**
```
User A skills: [Flutter, Firebase, Problem Solving]
User B skills: [Flutter, Dart, Problem Solving]

Intersection: [Flutter, Problem Solving] = 2 skills
Union: [Flutter, Firebase, Problem Solving, Dart] = 4 skills
Similarity Score: 2/4 = 0.5
```

---

### 5.2 Scheduled Background Tasks

The application uses Spring `@Scheduled` for automated cleanup and reminder jobs:

#### CleanupService
```java
@Scheduled(fixedDelay = 3600000) // Every 1 hour
public void deleteExpiredTeamFindingPosts()
```
- Finds all `TeamFindingPost` entries older than 24 hours
- Automatically deletes expired team recruitment posts
- Part of the 24-hour post lifecycle

#### TeamCleanupService
```java
@Scheduled(cron = "0 * * * * *") // Every minute
public void processExpiredTeamFindingPosts()
```
- Converts confirmed TeamFindingPosts to CollabPods
- Minimum 2 members required for conversion
- Creates pods with `type = TEAM` and linked `eventId`
- Refreshes event statistics after processing

#### ReminderService
```java
@Scheduled(fixedRate = 60000) // Every minute
public void checkAndSendReminders()
```
- Checks for due `EventReminder` records
- Sends notifications via WebSocket queue `/queue/user/{userId}`
- Reminder types:
  - ONE_DAY_BEFORE
  - ONE_HOUR_BEFORE
  - FIFTEEN_MINUTES_BEFORE

---

### 5.3 Notification Service (WebSocket Broadcasting)

**Methods:**
```java
notifyPodMembers(String podId, String message)
  → /topic/pod/{podId}

notifyUser(String userId, Object notification)
  → /queue/user/{userId}

notifyPodUpdate(String podId, Object update)
  → /topic/pod/{podId}/updates

notifyBuddyBeacon(String userId, Object beaconData)
  → /topic/campus/beacons
```

**Used by:**
- Application acceptance/rejection
- Pod bans
- Event reminders
- New comments
- Pod updates

---

### 5.4 Comment Threading Service (CommentService)

**Hierarchical Comment Structure:**
```
TopLevel Comment (parentId = null)
├── Reply 1 (parentId = comment_id)
├── Reply 2 (parentId = comment_id)
│   ├── Nested Reply (parentId = reply_2_id)
```

**Key Methods:**
```java
getCommentsForPost(String postId)           // Top-level only
getReplies(String postId, String parentId)   // Child comments
getAllCommentsForPost(String postId)         // Full tree
deleteComment(String commentId)              // Cascade deletes replies
```

**Cascade Delete Logic:**
- Deleting parent also removes all child comments
- Data consistency maintained automatically

---

### 5.5 Pod Message Service (PodMessageService)

**Features:**
- Separate `PodMessage` collection for scalability
- TTL Index: Messages auto-delete after 3 days
- Sorted by timestamp (oldest first)
- Cascade delete for pod cleanup

**Database Schema:**
```javascript
{
  _id: ObjectId,
  podId: String (indexed),
  senderId: String,
  senderName: String,
  content: String,
  replyToId: String,
  attachmentUrl: String,
  attachmentType: "IMAGE" | "FILE" | "NONE",
  timestamp: ISODate (TTL: 259200 seconds = 3 days)
}
```

---

### 5.6 Project Management Service (ProjectService)

**Entities:** Projects with milestones and tasks.

**Data Model:**
```json
{
  "id": "project_1",
  "name": "Mobile App MVP",
  "leaderId": "user_123",
  "memberIds": ["user_123", "user_456"],
  "maxTeamSize": 5,
  "status": "IN_PROGRESS",
  "startDate": "2025-12-01T00:00:00",
  "endDate": "2026-01-31T00:00:00",
  "milestones": [
    {
      "id": "milestone_1",
      "title": "UI/UX Design",
      "dueDate": "2025-12-15T00:00:00",
      "tasks": [
        {
          "id": "task_1",
          "description": "Wireframes",
          "assignedTo": "user_456",
          "completed": false
        }
      ]
    }
  ]
}
```

**Project Status Enum:**
- PLANNING
- IN_PROGRESS
- COMPLETED
- ON_HOLD
- CANCELLED

---

## 6. Gamification System (XP/Levels)

### XP Actions & Rewards

| Action | XP Reward |
|--------|-----------|
| Create post | +10 XP |
| Create comment | +5 XP |
| Like post | +2 XP |
| Create pod | +15 XP |
| Join pod | +5 XP |
| Create event | +25 XP |
| Receive endorsement | +10 XP |

### Level System

- **Level 0:** 0 XP (starting)
- **Level 1:** 100 XP cumulative
- **Level 2:** 200 XP cumulative
- **Level 3:** 300 XP cumulative
- (Increments of 100 XP per level)

### Achievement Badges

| Badge | Unlock Condition |
|-------|------------------|
| Signal Guardian | Create first post |
| Skill Sage | Receive 5 endorsements |
| Campus Catalyst | Promoted by developer (COLLEGE_HEAD role) |
| Collaboration Champion | Join 3 pods |
| Team Lead | Create pod and add 5+ members |

---

## 6. Campus Isolation & Data Access

### Campus Isolation Mechanism

**Implementation:** Denormalized `college` field on Posts, CollabPods, Comments

**Filtering Logic:**
1. Extract current user's college from User document
2. On GET requests for posts/pods, filter by `college` field
3. Users only see data from their own college for CAMPUS-scoped content
4. GLOBAL-scoped content is visible to all users

**Endpoints with Campus Isolation:**
- `GET /api/posts` → Filtered by college
- `GET /pods/campus` → Filtered by college  
- `GET /api/beacon/feed` → Filtered by college
- `GET /api/comments` → Filtered by college (if post is campus-scoped)

---

## 7. Error Handling

### Standard Error Response Format

```json
{
  "error": "Error message description",
  "code": "ERROR_CODE",
  "details": {
    "field": "Additional context"
  }
}
```

### Common HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | Request successful |
| 201 | Resource created |
| 204 | No content (delete success) |
| 400 | Bad request (invalid input) |
| 401 | Unauthorized (missing/invalid JWT) |
| 403 | Forbidden (insufficient permissions) |
| 404 | Not found |
| 409 | Conflict (e.g., user banned from pod) |
| 429 | Too many requests (cooldown active) |
| 500 | Server error |

### Custom Exception Types

- `PermissionDeniedException` → 403
- `CooldownException` → 429
- `BannedFromPodException` → 403

---

## 8. Configuration Reference

### Environment Variables (Required)

```bash
# Set these in your environment before running
JWT_SECRET=<min-32-chars-secret-key>
MONGO_URI=<see-render-dashboard-for-atlas-uri>
PORT=8080
```

### Application Properties

```properties
server.port=${PORT:8080}
jwt.secret=${JWT_SECRET:your-secret-key-here}
spring.data.mongodb.uri=${MONGO_URI}
spring.data.mongodb.database=<database-name>
file.upload.dir=${user.dir}/uploads
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

---

## 9. Security Best Practices for Mobile Clients

✅ **Do:**
- Store JWT token securely (Platform-specific secure storage)
- Include `Authorization` header on all protected requests
- Validate SSL certificates in production
- Implement token refresh logic (5-hour expiry)
- Use HTTPS only in production
- Hash sensitive data locally before transmission
- Implement rate limiting on client side

❌ **Don't:**
- Store JWT in SharedPreferences/UserDefaults (use Keychain/Keystore)
- Log JWT tokens in debug/production logs
- Send tokens in URL query parameters
- Hardcode API URLs or secrets in code
- Trust client-side validation alone

---

## 10. Testing the API (cURL Examples)

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'
```

### Get User Profile
```bash
curl -X GET http://localhost:8080/api/users/user_uuid_123 \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### Create Social Post
```bash
curl -X POST http://localhost:8080/api/posts/social \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "title":"Need help",
    "content":"Flutter layout help",
    "type":"ASK",
    "requiredSkills":["Flutter"]
  }'
```

### Subscribe to Pod Chat (WebSocket)
```bash
# Using a WebSocket client library in your mobile app
CONNECT
login:<JWT_TOKEN>
accept-version:1.2
heart-beat:0,0

^@
SUBSCRIBE
id:sub-0
destination:/topic/pod.pod_uuid_1.chat

^@
```

---

## 11. Quick Reference: API Route Summary

| Feature | Endpoints |
|---------|-----------|
| **Auth** | POST /auth/login, POST /auth/register, GET /auth/me |
| **Users** | GET /users/{id}, PUT /users/{id}, GET /users/{id}/xp, GET /users/{id}/achievements |
| **Posts** | GET/POST /posts, GET/POST /posts/social, PUT /posts/{id}/like |
| **Comments** | GET/POST /comments, DELETE /comments/{id} |
| **Pods** | GET/POST /pods, GET /pods/{id}, POST /pods/{id}/join |
| **Beacons** | POST /beacon, GET /beacon/feed, POST /beacon/apply/{id} |
| **Events** | GET/POST /events, GET /events/{id}, POST /events/{id}/register-click |
| **Inbox** | GET /inbox/my, PATCH /inbox/{id}/read, DELETE /inbox/{id} |
| **Messages** | GET /messages/conversations/{userId}, POST /messages/invite/{targetId} |
| **Discovery** | GET /discovery/mesh |
| **Files** | POST /uploads/pod-files |

---

## 12. Troubleshooting Guide

### Common Issues

**Issue:** `401 Unauthorized` on all requests
- **Solution:** Check JWT token is included in `Authorization` header with "Bearer " prefix

**Issue:** `403 Forbidden` when trying to access pod
- **Solution:** Verify user is a member of the pod (check `memberIds`)

**Issue:** Campus isolation not working (seeing posts from other colleges)
- **Solution:** Ensure posts have `college` field populated matching current user's college

**Issue:** WebSocket messages not received
- **Solution:** 
  - Verify WebSocket connection is established (`/ws-studcollab`)
  - Check subscription destination matches the topic path
  - Verify JWT token is included in WebSocket handshake

**Issue:** File upload fails
- **Solution:** 
  - Check file size < 10MB
  - Verify `Content-Type` is `multipart/form-data`
  - Ensure `uploads/` directory exists and is writable

---

## 13. Application Initialization & Startup

### Main Application Entry Point

**File:** `ServerApplication.java`

```java
@SpringBootApplication
@EnableScheduling  // Enables @Scheduled background tasks
public class ServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
```

**On Startup:**
1. Spring loads all configurations (`SecurityConfig`, `WebSocketConfig`, `MongoConfig`, `AppConfig`)
2. Establishes MongoDB connection via `MONGO_URI` environment variable
3. Initializes JWT utility with `JWT_SECRET` environment variable
4. Registers all repositories (auto-generated Spring Data interfaces)
5. Initializes all services with autowired dependencies
6. Enables scheduled tasks (cleanup, team processing, reminders)
7. Registers STOMP endpoint at `/ws-studcollab`
8. Starts embedded Tomcat server on `PORT` (default 8080)

---

## 14. Request Flow & Processing Pipeline

### Typical REST Request Flow

```
1. HTTP Request arrives at controller endpoint
   ↓
2. CORS filter validates origin (SecurityConfig)
   ↓
3. JwtRequestFilter extracts JWT from header/cookie
   ↓
4. JwtUtil validates token signature & expiration
   ↓
5. Spring Security applies authorization rules
   ↓
6. RequestLoggingFilter logs (especially uploads)
   ↓
7. Controller method executes
   ↓
8. Service layer processes business logic
   ↓
9. Repository executes MongoDB query
   ↓
10. Response returned (JSON serialized by Jackson)
   ↓
11. HTTP Response sent to client
```

### Typical WebSocket Message Flow

```
1. Client connects to /ws-studcollab
   ↓
2. STOMP handshake established
   ↓
3. Client sends message to /app/{destination}
   ↓
4. Mapped controller method receives @MessageMapping
   ↓
5. Service layer processes (saves to DB, etc.)
   ↓
6. SimpMessagingTemplate broadcasts to /topic/{path}
   ↓
7. All subscribed clients receive message
```

---

## 15. File Upload Flow & Storage

### Upload Request Processing

```
POST /api/uploads/pod-files
├─ RequestLoggingFilter logs upload details
├─ FileUploadController validates file
├─ Creates /uploads directory if missing
├─ Generates UUID filename
├─ Copies file to disk
├─ Returns URL: /uploads/{uuid}.{ext}
└─ Client stores URL in database
```

**Upload Constraints:**
- Max file size: 10MB
- Max request size: 10MB
- Allowed types: Any (no MIME type filtering)

**File Storage:**
- Location: `{project.root}/uploads/`
- Files NOT persisted across container restarts
- ⚠️ Production: Use AWS S3 or Cloudinary for persistence

**File Serving:**
- Resource handler at `/uploads/**`
- Maps to `file:{user.dir}/uploads/`
- Served statically without authentication

---

## 16. Database Schema & Indexes

### MongoDB Collections with Indexes

| Collection | Indexes | TTL | Notes |
|-----------|---------|-----|-------|
| users | email (unique), collegeName, isDev | None | User profiles |
| posts | createdAt (asc), college, type | None | Base collection for all post types |
| comments | postId, parentId, authorId, createdAt | None | Threaded comments |
| collabPods | scope, college, type, createdAt | None | Collaboration pods |
| messages | conversationId, podId, senderId, timestamp | None | DMs and pod chats |
| beacons | eventId, college, status, createdAt | None | Buddy beacon posts |
| events | category, startDate, linkEndDate | None | Events |
| inbox | userId, read, type, createdAt | None | User notifications |
| applications | beaconId, applicantId, status | None | Applications to beacons |
| achievements | userId, name | None | Achievements/badges |
| projects | leaderId, status, startDate | None | Projects |
| podMessages | podId, timestamp (TTL: 3 days) | 259200s | Pod chat history (auto-delete) |
| podCooldowns | userId, podId, expiryDate (TTL: 15 min) | 900s | Rate-limiting (auto-delete) |
| conversations | participantIds | None | DM threads |
| eventReminders | eventId, userId, reminderTime | None | Event reminders |

### Denormalization Strategy

To optimize read performance in campus isolation:

```javascript
// Post document includes denormalized college field
{
  _id: ObjectId,
  college: "SINHGAD",    // Denormalized from User
  authorId: "user_123",  // Reference (not denormalized)
  ...
}

// CollabPod document includes denormalized college
{
  _id: ObjectId,
  college: "SINHGAD",    // Denormalized for fast filtering
  scope: "CAMPUS",
  ...
}
```

---

## 17. Error Response Format & Status Codes

### Standard Error Response

```json
{
  "error": "Description of what went wrong",
  "code": "ERROR_CODE",
  "details": {
    "field": "Additional context",
    "reason": "Why the error occurred"
  }
}
```

### HTTP Status Code Reference

| Code | Meaning | Example |
|------|---------|---------|
| 200 | Request successful | GET profile |
| 201 | Resource created | POST /posts |
| 204 | No content (success) | DELETE comment |
| 400 | Bad request | Invalid email format |
| 401 | Unauthorized | Missing/expired JWT |
| 403 | Forbidden | User banned from pod |
| 404 | Not found | Pod ID doesn't exist |
| 409 | Conflict | Cooldown active |
| 429 | Too many requests | Rate limit exceeded |
| 500 | Server error | MongoDB connection failed |

### Custom Exception Handling

```java
@ExceptionHandler(PermissionDeniedException.class)
→ ResponseEntity.status(403)

@ExceptionHandler(CooldownException.class)
→ ResponseEntity.status(429)

@ExceptionHandler(BannedFromPodException.class)
→ ResponseEntity.status(403)
```

---

## 18. Performance Considerations & Optimization

### Indexed Queries (Fast)
```javascript
db.posts.find({college: "SINHGAD"})     // Indexed
db.comments.find({postId: "post_1"})    // Indexed
db.messages.find({podId: "pod_1"})      // Indexed
db.podCooldowns.findOne({expiryDate: {$lt: now}})  // TTL + Indexed
```

### Unindexed Queries (Slow - Avoid)
```javascript
db.posts.find({content: "keyword"})     // Full collection scan
db.users.find({skills: {$in: [array]}}) // Array element scan
```

### Aggregation Pipeline Optimization
```java
// Used in PostController for post statistics
Aggregation agg = Aggregation.newAggregation(
    Aggregation.match(Criteria.where("college").is(college)),
    Aggregation.group("type").count().as("count"),
    Aggregation.sort(Sort.Direction.DESC, "count")
);
```

### WebSocket Performance
- Messages broadcasted to `/topic/*` reach all subscribers
- User-specific messages via `/queue/user/{userId}` (P2P)
- SimpMessagingTemplate handles thread-safe broadcasting
- Consider message compression for large payloads

---

## 19. Common Development Patterns

### Campus Isolation Pattern
```java
// Extract user's college
String userCollege = currentUser.getCollegeName();

// Filter results by college
List<Post> posts = postRepository.findByCollege(userCollege);
```

### Role-Based Access Pattern
```java
if (!currentUser.isDev() && !currentUser.isRole("COLLEGE_HEAD")) {
    return ResponseEntity.status(403).build();
}
```

### Scheduled Task Pattern
```java
@Scheduled(fixedDelay = 3600000)  // Every 1 hour
public void performCleanup() {
    // Do work
}
```

### Entity Validation Pattern
```java
if (user == null) {
    throw new RuntimeException("User not found");
}
```

---

## 20. Migration & Deployment Considerations

### Database Migration
- No schema migrations needed (MongoDB schema-less)
- Document structure evolves organically
- Old/new versions coexist in collections
- See `mongodb-schema-upgrade.js` for manual migrations

### Environment Configuration
```bash
# Set via Render dashboard or .env file
PORT=8080
JWT_SECRET=<min-32-chars-secret>
MONGO_URI=<configure-in-render-or-local-env>
```

### Container Deployment (Render, Heroku)
- `PORT` environment variable must be respected
- Files uploaded to `/uploads/` will be deleted on restart
- Set `JAVA_OPTS=-Xmx512m` to limit heap (shared container)
- Use managed MongoDB Atlas (never host Mongo in container)

---

## Appendix A: MongoDB Collections Schema

### Collection: users
```javascript
{
  _id: ObjectId,
  fullName: String,
  email: String,
  password: String (hashed),
  collegeName: String,
  yearOfStudy: String,
  department: String,
  skills: [String],
  goals: String,
  badges: [String],
  level: Number,
  xp: Number,
  totalXp: Number,
  endorsementsCount: Number,
  role: String (STUDENT|COLLEGE_HEAD),
  isDev: Boolean,
  createdAt: ISODate
}
```

### Collection: posts
```javascript
{
  _id: ObjectId,
  authorId: String,
  type: String (via discriminator),
  content: String,
  college: String,
  createdAt: ISODate,
  // For SocialPost
  title: String,
  likes: [String],
  commentIds: [String],
  pollOptions: [{id, text, votes}],
  // For TeamFindingPost
  maxTeamSize: Number,
  currentTeamMembers: [String]
}
```

### Collection: collabPods
```javascript
{
  _id: ObjectId,
  name: String,
  description: String,
  ownerId: String,
  memberIds: [String],
  adminIds: [String],
  scope: String (CAMPUS|GLOBAL),
  college: String,
  type: String,
  status: String,
  createdAt: ISODate
}
```

---

**Document Version:** 1.0  
**Last Updated:** December 2025  
**Backend Version:** Spring Boot 3.2.5 (Java 17)
