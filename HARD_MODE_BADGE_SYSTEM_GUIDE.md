# Hard-Mode Badge System Implementation Guide

## Overview

The Hard-Mode Badge System is an elite achievement framework designed to drive deep engagement in the Tessera platform through rigorous, strict unlock criteria. The system enforces a 2-badge-per-day unlock limit and features 20 unique badges organized by tier.

## Architecture

### 1. **Data Models**

#### User Model Extensions

Located in: `User.java`

New fields added:

- `totalReplies` (int) - Total replies across all posts
- `weeklyReplies` (int) - Replies in current week (resets every Monday)
- `loginStreak` (int) - Consecutive days logged in
- `lastLoginDate` (LocalDate) - Last date user logged in
- `lastUnlockDate` (LocalDate) - Last date a hard-mode badge was unlocked
- `dailyUnlocksCount` (int) - How many badges unlocked today (max 2)
- `hardModeBadgesEarned` (List) - Earned hard-mode badges
- `hardModeBadgesLocked` (List) - Badges awaiting unlock (blocked by daily limit)
- `statsMap` (Map<String, Integer>) - Tracks: helpNeededReplies, pinnedResources, correctPolls, etc.

#### HardModeBadge Model

Located in: `HardModeBadge.java`

Represents individual badge tracking records:

```java
- userId: String
- badgeId: String (e.g., "discussion-architect")
- badgeName: String
- tier: String (LEGENDARY, EPIC, RARE, COMMON, PENALTY)
- visualStyle: String (e.g., "gold-glow", "purple-shimmer")
- progressCurrent: int
- progressTotal: int
- isUnlocked: boolean
- isEquipped: boolean
- unlockedAt: LocalDateTime
- equippedAt: LocalDateTime
- progressData: Map<String, Object>
```

### 2. **Service Layer**

#### HardModeBadgeService

Located in: `HardModeBadgeService.java`

**Core Methods:**

1. **Initialization**

   ```java
   initializeHardModeBadgesForUser(String userId)
   ```

   Called when a new user registers. Creates 20 badge records with 0 progress.

2. **Daily Limit Enforcement**

   ```java
   canUnlockMoreToday(String userId): boolean
   getRemainUnlocksToday(String userId): int
   tryUnlockBadge(String userId, String badgeId): Map<String, Object>
   ```

   Enforces max 2 badge unlocks per 24-hour period.

3. **Badge Criteria Checking**

   ```java
   checkAndUnlockBadgeCriteria(String userId, String badgeId): void
   evaluateBadgeCriteria(HardModeBadge badge, User user): boolean
   ```

   Checks if badge unlock criteria are met and auto-unlocks.

4. **Action Tracking**

   ```java
   trackReplyAction(String userId, String replyType, Map<String, Object> metadata)
   trackLogin(String userId)
   ```

   Tracks user activities that contribute to badge progress.

5. **Scheduled Maintenance**
   ```java
   @Scheduled midnightMaintenanceTask()  // 0 0 0 * * *
   @Scheduled weeklyResetTask()          // 0 0 0 * * MON
   ```

   - Resets daily unlock count at midnight
   - Checks login streaks
   - Resets weekly reply count every Monday

### 3. **Backend Integration Points**

#### UserService

- `findOrCreateUserByOauth()` - Initializes hard-mode badges for new OAuth users
- `register()` - Initializes hard-mode badges for new email-registered users

#### AuthenticationController

- `authenticate()` - Calls `hardModeBadgeService.trackLogin()` on successful login

#### CommentService

- `addComment()` - Calls `hardModeBadgeService.trackReplyAction()` when comment is created

### 4. **Controller Endpoints**

#### BadgeController

Located in: `BadgeController.java`

**Available Endpoints:**

```
GET  /api/badges/hard-mode/{userId}
     Returns all hard-mode badges with current progress and status

POST /api/badges/hard-mode/{userId}/unlock/{badgeId}
     Attempts to unlock a specific badge (enforces daily limit)
     Returns: { success, message, badgeName, tier, visualStyle, status, remainingTime }

GET  /api/badges/hard-mode/{userId}/remaining-unlocks
     Gets remaining badge unlocks for today

POST /api/badges/hard-mode/{userId}/track-reply
     Manually tracks a reply action
     Body: { replyType: "help-needed-first-reply", metadata: {...} }

POST /api/badges/hard-mode/{userId}/track-login
     Manually tracks a login event
```

### 5. **Frontend Integration**

#### BadgeCenter.jsx

Located in: `BadgeCenter.jsx`

**New Features:**

1. **Hard-Mode Tab**
   - Fetches all hard-mode badges from backend
   - Displays loading state while fetching
   - Shows "Elite (Hard-Mode) Badges" section with explanation

2. **Badge Status Display**
   - **Equipped**: Blue border, "✓ EQUIPPED" badge
   - **Pending Unlock**: Yellow border, "⏳ PENDING" badge with countdown
   - **Unlocked**: Purple border, "Equip Badge" button
   - **Locked**: Gray, grayed-out appearance

3. **Visual Styles**
   - Maps each badge's `visualStyle` to Tailwind glow effects
   - Animated effects for certain tiers (fire, pulse, etc.)
   - Progress bars showing current/total progress

4. **User Actions**
   - Click "Equip Badge" to unlock an unlocked badge (if daily limit allows)
   - Automatic countdown timer for pending badges
   - WebSocket real-time updates for badge unlocks

5. **WebSocket Integration**
   - Subscribes to `/user/{userId}/queue/badge-unlock` for real-time notifications
   - Auto-refreshes badge data when new unlock occurs

## 20 Elite Badges

### Badge Definitions with Unlock Criteria

| Badge ID              | Badge Name               | Criteria                                | Tier      | Visual Style      |
| --------------------- | ------------------------ | --------------------------------------- | --------- | ----------------- |
| discussion-architect  | Discussion Architect     | 50+ replies on single post              | LEGENDARY | gold-glow         |
| active-talker-elite   | Active Talker (Elite)    | 150 replies in 7 days                   | EPIC      | purple-shimmer    |
| ultra-responder       | Ultra-Responder          | 20 fast replies (<30 sec)               | RARE      | electric-blue     |
| midnight-legend       | Midnight Legend          | 3 nights of midnight replies (2-4 AM)   | RARE      | dark-moon-glow    |
| bridge-master         | Bridge Master            | DMs with 5 colleges in 24h              | EPIC      | green-aurora      |
| doubt-destroyer       | Doubt Destroyer          | First reply to 25 #HelpNeeded questions | EPIC      | ruby-red          |
| resource-titan        | Resource Titan           | 25 resources pinned by others           | LEGENDARY | emerald-shine     |
| lead-architect        | Lead Architect           | 10 multi-college collab rooms           | LEGENDARY | molten-gold       |
| team-engine           | Team Engine              | 15 collab rooms + 20+ replies each      | EPIC      | cobalt-steel      |
| first-responder       | First Responder          | First reply within 30 minutes           | COMMON    | silver-gloss      |
| streak-seeker-lvl3    | Streak Seeker (Lvl 3)    | 100 consecutive login days              | LEGENDARY | animated-fire     |
| collab-master-lvl3    | Collab Master (Lvl 3)    | 50 different collab rooms               | EPIC      | cyan-pulse        |
| voice-of-hub-lvl3     | Voice of the Hub (Lvl 3) | 1500 total replies                      | LEGENDARY | solar-flare       |
| profile-perfectionist | Profile Perfectionist    | Full profile + 30-day maintenance       | COMMON    | polished-chrome   |
| the-oracle-gm         | The Oracle (GM)          | 50 correct poll predictions             | EPIC      | amethyst-eye      |
| silent-sentinel       | Silent Sentinel          | 500 replies + 0 reports                 | RARE      | white-marble      |
| campus-helper         | Campus Helper            | 10 replies marked helpful               | COMMON    | bronze-oak        |
| event-vanguard        | Event Vanguard           | Reply to event within 5 minutes         | RARE      | orange-neon       |
| cross-domain-pro      | Cross-Domain Pro         | Collab rooms in 5 academic branches     | EPIC      | multicolor-prism  |
| spam-alert-sanction   | Spam Alert (Sanction)    | Triggered by valid report               | PENALTY   | red-pulsing-cross |

## System Mechanics

### Daily Unlock Limit: 2 per 24 Hours

**How It Works:**

1. User earns a badge by meeting its criteria
2. Badge status changes to "Unlocked"
3. User clicks "Equip Badge" button
4. System checks: `canUnlockMoreToday(userId)`
5. If true: Badge equipped, `dailyUnlocksCount` incremented
6. If false: Badge moved to `hardModeBadgesLocked`, status = "pending-unlock"
7. At midnight, `midnightMaintenanceTask()` unlocks the first pending badge
8. User receives WebSocket notification

**Frontend Countdown:**

- When pending, a countdown timer shows hours until midnight
- Automatically updates as time passes
- Can manually refresh to see updated time

### Progress Tracking

**Automatic Tracking:**

- Reply created → `trackReplyAction("reply", metadata)` called
- User logs in → `trackLogin(userId)` called
- Metrics updated in real-time via MongoDB

**Manual Tracking (via API):**

```bash
POST /api/badges/hard-mode/{userId}/track-reply
{
  "replyType": "help-needed-first-reply",
  "metadata": {
    "postId": "xyz",
    "timestamp": 1234567890
  }
}
```

### Maintenance Badges

Certain badges require active maintenance:

1. **Streak Seeker (Lvl 3)**
   - Requires: Logging in every day
   - If missed: Streak resets to 0 at midnight
   - If equipped and streak breaks: Badge auto-removed

2. **Profile Perfectionist**
   - Requires: All profile fields filled + Project Links updated within 30 days
   - If not maintained: Badge auto-removed
   - Must re-unlock next month

### Scheduled Tasks

#### Midnight Maintenance (Daily at 00:00)

```
- Reset dailyUnlocksCount to 0
- Check login streaks (if lastLoginDate > 24h ago, streak = 0)
- Unlock first pending badge (if any)
- Remove maintenance badges if criteria failed
```

#### Weekly Reset (Every Monday at 00:00)

```
- Reset weeklyReplies to 0 for all users
```

## Integration Checklist

### Backend Setup

- [x] Update User model with new fields
- [x] Create HardModeBadge model
- [x] Create HardModeBadgeRepository
- [x] Create HardModeBadgeService
- [x] Create BadgeController
- [x] Update UserService to initialize badges
- [x] Update AuthenticationController to track login
- [x] Update CommentService to track replies
- [x] Add scheduled tasks for maintenance

### Frontend Setup

- [x] Update BadgeCenter.jsx
- [x] Add hard-mode badge tab
- [x] Implement unlock button and countdown
- [x] Add WebSocket listener for real-time updates
- [x] Implement visual style mapping for badge glows

### Testing

1. **New User Registration**
   - Verify 20 badges initialized with 0 progress
   - Verify badges appear in BadgeCenter

2. **Login Tracking**
   - Login with user
   - Check `lastLoginDate` and `loginStreak` updated
   - Verify Streak Seeker progress updated

3. **Reply Tracking**
   - Create a reply/comment
   - Check `totalReplies` and `weeklyReplies` incremented
   - Verify badge progress updated

4. **Badge Unlock**
   - Manually meet a badge's criteria (or update DB)
   - Verify badge shows as "Unlocked"
   - Click equip button
   - Verify badge equipped (if under daily limit)

5. **Daily Limit**
   - Equip 2 badges
   - Try to equip a 3rd badge
   - Verify it goes to "pending-unlock" with countdown
   - Wait for midnight maintenance
   - Verify first pending badge auto-unlocks

6. **Maintenance Badges**
   - Equip Streak Seeker
   - Don't log in tomorrow
   - At next midnight, verify badge removed
   - Verify loginStreak reset to 0

## API Response Examples

### GET /api/badges/hard-mode/{userId}

```json
{
  "badges": [
    {
      "badgeId": "discussion-architect",
      "badgeName": "Discussion Architect",
      "tier": "LEGENDARY",
      "visualStyle": "gold-glow",
      "progress": { "current": 45, "total": 50 },
      "isUnlocked": false,
      "isEquipped": false,
      "status": "locked"
    },
    {
      "badgeId": "active-talker-elite",
      "badgeName": "Active Talker (Elite)",
      "tier": "EPIC",
      "visualStyle": "purple-shimmer",
      "progress": { "current": 150, "total": 150 },
      "isUnlocked": true,
      "isEquipped": false,
      "status": "pending-unlock",
      "remainingTime": 7200000
    }
  ],
  "totalBadges": 20,
  "equippedCount": 5
}
```

### POST /api/badges/hard-mode/{userId}/unlock/{badgeId}

**Success Response (200)**

```json
{
  "success": true,
  "message": "🎉 Active Talker (Elite) badge unlocked!",
  "badgeName": "Active Talker (Elite)",
  "tier": "EPIC",
  "visualStyle": "purple-shimmer"
}
```

**Daily Limit Exceeded (429)**

```json
{
  "success": false,
  "status": "pending-unlock",
  "message": "Daily unlock limit reached. Badge waiting for tomorrow.",
  "remainingTime": 7200000
}
```

**Criteria Not Met (400)**

```json
{
  "success": false,
  "message": "Badge criteria not met. Progress: 45/50",
  "progress": { "current": 45, "total": 50 }
}
```

## Best Practices

1. **Always Check Daily Limit** Before showing unlock UI

   ```javascript
   GET / api / badges / hard - mode / { userId } / remaining - unlocks;
   ```

2. **Use WebSocket** for Real-Time Updates
   - Subscribe to badge unlock messages
   - Auto-refresh badge list on unlock
   - Show toast notifications

3. **Track All Relevant Actions**
   - Every reply → track it
   - Every login → track it
   - Every poll vote → track it

4. **Test Midnight Tasks**
   - Use cron expressions to verify
   - Monitor application logs
   - Manually trigger if needed for testing

5. **Cache Badge Data** on Frontend
   - Reduce API calls
   - Use React Context or Redux
   - Invalidate on unlock

## Troubleshooting

### Badges Not Initializing

- Check HardModeBadgeService is autowired in UserService
- Verify MongoDB is running and connected
- Check application logs for exceptions

### Unlock Endpoint Returning 401

- Verify JWT token in request headers
- Check AuthenticationController is passing token correctly
- Verify user exists in database

### Daily Limit Not Working

- Check `lastUnlockDate` is being set in database
- Verify `canUnlockMoreToday()` logic
- Check system time/timezone settings

### Midnight Tasks Not Running

- Verify Spring Scheduling is enabled (@EnableScheduling)
- Check application logs for scheduled task execution
- Verify cron expressions are correct

### Progress Not Updating

- Verify CommentService is calling `trackReplyAction()`
- Check HardModeBadgeService has @Autowired annotation
- Manually call `/api/badges/hard-mode/{userId}/track-reply` to debug

## Performance Considerations

1. **Batch Updates** - Consider bulk operations for large user bases
2. **Indexed Queries** - Add MongoDB indexes on `userId`, `badgeId`, `isUnlocked`
3. **Cache** - Cache badge definitions in memory (they don't change)
4. **Pagination** - Future: paginate badge list for users with 100+ badges
5. **Archive** - Archive old badge records after 1 year

## Future Enhancements

1. **Badge Trading** - Allow users to trade badges
2. **Leaderboards** - Show top badge earners
3. **Badge Rarity** - Track how many users have each badge
4. **Badge Events** - Limited-time badges for special events
5. **Badge Sets** - Collect all badges in a category for bonus reward
6. **Badge Analytics** - Dashboard showing badge unlock trends

---

**Last Updated:** February 12, 2026
**Version:** 1.0
**Status:** Production Ready
