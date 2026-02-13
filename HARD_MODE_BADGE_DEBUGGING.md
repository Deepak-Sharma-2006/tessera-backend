# Hard-Mode Badge System - Debugging Guide

## Quick Diagnostics

### 1. Check Browser Console for Errors

Press `F12` in your browser and check the Console tab for any errors when the page loads.

**Look for:**

- Red error messages from the `/api/badges/hard-mode/{userId}` endpoint
- Network errors (CORS, 404, 500, etc.)
- Messages starting with `[BadgeCenter]`

### 2. Verify User ID

The diagnostic UI will show your user ID. Make sure it's a valid MongoDB ObjectID (looks like: `507f1f77bcf86cd799439011`)

### 3. Test the Backend Directly

#### Using cURL (Command Line)

```bash
curl -X GET "http://localhost:8080/api/badges/hard-mode/YOUR_USER_ID"
```

Replace `YOUR_USER_ID` with your actual user ID from the browser console or UI.

Expected response:

```json
{
  "badges": [
    {
      "badgeId": "discussion-architect",
      "badgeName": "Discussion Architect",
      "tier": "LEGENDARY",
      ...
    },
    ...20 badges total...
  ],
  "totalBadges": 20,
  "equippedCount": 0
}
```

#### Using Postman

1. Create new GET request
2. URL: `http://localhost:8080/api/badges/hard-mode/YOUR_USER_ID`
3. Send request
4. Check response in "Response" tab

### 4. Check Server Logs

#### Look for these logs:

```
[BadgeController] 📥 GET /api/badges/hard-mode/{userId}
[HardModeBadgeService] 🔍 Fetching badges for user: {userId}
[HardModeBadgeService] 📊 Found 20 badges
```

If you see:

```
[HardModeBadgeService] ⚠️ No badges in database for user {userId}
[BadgeController] ⚠️ No badges found for user {userId}, auto-initializing...
```

**Then** it's initializing badges for the first time (this is normal).

#### If you see errors:

```
[BadgeController] ❌ Error fetching badges: ...
```

The error message will tell you what went wrong.

### 5. MongoDB Verification

Connect to MongoDB Atlas and check:

```javascript
// Check if hard-mode badges collection exists
db.hardModeBadges.count(); // Should return number > 0

// Check your user's badges
db.hardModeBadges.find({ userId: "YOUR_USER_ID" }); // Should return 20 documents

// Check if user has badge fields
db.users.findOne({ _id: ObjectId("YOUR_USER_ID") }); // Should have hardModeBadgesEarned, etc.
```

## Common Issues & Fixes

### Issue: "No hard-mode badges found" message appears

**Cause:** Badges not initialized in database

**Fix:**

1. Click "Retry Loading" button in the UI
2. Wait for auto-initialization to complete
3. Or manually call the initialization:
   ```bash
   curl -X POST "http://localhost:8080/api/badges/hard-mode/initialize/YOUR_USER_ID"
   ```

### Issue: API returns 500 error

**Cause:** Server error (check logs)

**Fix:**

1. Look at server console for full error
2. Verify MongoDB connection is working
3. Check if `HardModeBadgeService` is properly autowired
4. Restart Spring Boot application

### Issue: API returns 404

**Cause:** Endpoint not found

**Fix:**

1. Verify BadgeController is compiled and deployed
2. Check if Spring Boot application restarted after code changes
3. Verify endpoint path is correct: `/api/badges/hard-mode/{userId}`

### Issue: CORS error in browser console

**Cause:** Backend CORS settings blocking frontend

**Fix:**

1. Verify `@CrossOrigin` annotation on BadgeController:
   ```java
   @CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
   ```
2. Restart Spring Boot application

### Issue: User ID is undefined or null

**Cause:** User not logged in or session issue

**Fix:**

1. Make sure you're logged in
2. Refresh the page after login
3. Clear browser cookies and log back in

## Development Workflow

### 1. Code Changes

```bash
# Make changes to:
# - HardModeBadgeService.java
# - BadgeController.java
# - BadgeCenter.jsx
```

### 2. Restart Backend (if Java files changed)

```bash
# Stop current process: Ctrl+C in terminal
# Run: mvn spring-boot:run
```

### 3. Restart Frontend (if JSX files changed)

```bash
# The dev server usually auto-reloads
# If not: Ctrl+C and npm run dev
```

### 4. Test in Browser

```bash
# Clear browser cache: Ctrl+Shift+Delete
# Open DevTools: F12
# Reload page: F5 or Ctrl+R
# Watch console for logs
```

## Enable Debug Mode

### Backend Debug Logging

Add this to your application.properties:

```properties
logging.level.com.studencollabfin.server.service.HardModeBadgeService=DEBUG
logging.level.com.studencollabfin.server.controller.BadgeController=DEBUG
```

### Frontend Debug Logging

Already included in updated BadgeCenter.jsx. Check console with `F12`.

## Check if Auto-Initialization is Working

After logging in for the first time:

1. Open DevTools (`F12`)
2. Go to Networks tab
3. Look for request: `GET /api/badges/hard-mode/{userId}`
4. Response should show:
   ```
   ⚠️ No badges found, auto-initializing...
   ✅ Returning 20 badges
   ```

This means badges are being created automatically! Refresh the page and they should display.

## Manual Initialization (Admin)

If you need to manually initialize badges for a user, run this in MongoDB:

```javascript
// Get user ID
const userId = "YOUR_USER_ID";

// Create 20 badge documents
db.hardModeBadges.insertMany([
  {
    userId: userId,
    badgeId: "discussion-architect",
    badgeName: "Discussion Architect",
    tier: "LEGENDARY",
    visualStyle: "gold-glow",
    progressCurrent: 0,
    progressTotal: 50,
    isUnlocked: false,
    isEquipped: false,
  },
  // ... repeat for all 20 badges
]);
```

Or trust the auto-initialization to happen on first API call.

## Getting Help

If you still see no badges after trying fixes:

1. Share server console output
2. Share browser console output (F12 → Console tab)
3. Verify user ID and MongoDB connection
4. Check that all Java files were deployed correctly

---

**Last Updated:** February 12, 2026
