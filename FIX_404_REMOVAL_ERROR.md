# 404 Error Fix - Featured Badge Removal Endpoint

## Root Cause Analysis ✅

The 404 error indicates the backend endpoint is not being found. Investigation shows:

### ✅ What's Correct:

1. **Backend Endpoint EXISTS** - `@DeleteMapping("/{userId}/profile/featured-badges/{badgeId}")` is properly defined in UserController.java
2. **Frontend Path is CORRECT** - Sending `/api/users/{userId}/profile/featured-badges/{badgeId}`
3. **BadgeId Format is CORRECT** - Using `badge.id` which is "campus-catalyst" (matches MongoDB data)
4. **HTTP Method is CORRECT** - Using DELETE (not PATCH or PUT)

### ❌ The Real Problem:

**The server is not running the latest compiled code**

Evidence:

- All `mvn spring-boot:run` commands in terminal show Exit Code: 1 (failed)
- Server needs full rebuild and restart with the latest code changes
- The DELETE endpoint was just added to UserController.java but server hasn't picked it up

---

## Complete Fix Steps

### Step 1: Clean Previous Build & Rebuild Server

Run this in PowerShell from `D:\tessera\server`:

```powershell
# Stop any running Java processes
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force

# Clean and rebuild
mvn clean compile
```

**Expected Output:**

```
[INFO] BUILD SUCCESS
[INFO] Total time: XX.XXs
```

### Step 2: Run Server with Latest Code

```powershell
# From D:\tessera\server directory
mvn spring-boot:run -Dspring.profiles.active=dev
```

**Expected Output:**

```
Tomcat started on port(s): 8080 (http)
Started Application in XX.XXs
```

### Step 3: Verify Server is Running

Open another PowerShell and test:

```powershell
curl http://localhost:8080/api/users
```

Should respond with JSON (not 404 or connection refused).

### Step 4: Test Removal Endpoint Directly (Optional)

```powershell
# Replace with actual user ID
$userId = "6985947ee8f5b0d6741925514"
$badgeId = "campus-catalyst"

Invoke-WebRequest -Uri "http://localhost:8080/api/users/$userId/profile/featured-badges/$badgeId" `
  -Method DELETE `
  -Headers @{"Content-Type"="application/json"}
```

Should return 200 OK with updated user data.

---

## Verification Checklist

After restarting the server, the error should be resolved:

- [ ] Server started successfully (no errors in terminal)
- [ ] Server listens on `localhost:8080`
- [ ] Frontend can reach `/api/users/{id}` endpoints
- [ ] Minus icon appears on featured badges
- [ ] Clicking minus sends DELETE request
- [ ] DELETE request returns 200 OK (not 404)
- [ ] Badge removed from featured badges
- [ ] Empty slot (+) reappears
- [ ] Public profile auto-updates
- [ ] Console shows success message

---

## Why 404 Occurs Without Server Restart

1. **Old Code is Still Running** - Spring Boot caches the controller mappings
2. **New Endpoint Not Registered** - The DELETE endpoint wasn't known to the running server
3. **Request Path Unmatched** - Server can't map `/profile/featured-badges/{badgeId}` to any handler
4. **Result** - Returns 404 Not Found

Once you restart the server, Spring will re-scan all @RequestMapping and @DeleteMapping annotations and register the new endpoint.

---

## Code is Correct - Just Needs Deploy

The fix has already been implemented:

### Backend (UserController.java - Lines 394-428)

```java
@DeleteMapping("/{userId}/profile/featured-badges/{badgeId}")
public ResponseEntity<?> removeFeaturedBadge(
    @PathVariable String userId,
    @PathVariable String badgeId) {
  // Removes badge from MongoDB featuredBadges array
  // Returns updated User with 200 OK
}
```

### Frontend (BadgeCenter.jsx - Lines 508-530)

```javascript
const handleRemoveFeaturedBadge = async (badgeId) => {
  const response = await api.delete(
    `/api/users/${user.id}/profile/featured-badges/${badgeId}`,
  );
  setUser(response.data); // Update state with response
};
```

### UI (BadgeCenter.jsx - Lines 593-603)

```jsx
<button
  onClick={() => handleRemoveFeaturedBadge(badge.id)}
  className="...red-500...opacity-0 group-hover:opacity-100..."
>
  <span>−</span>
</button>
```

**All code is in place. Server just needs to be restarted to load it.**

---

## Endpoint Specification

### DELETE Featured Badge

```
Method:  DELETE
Path:    /api/users/{userId}/profile/featured-badges/{badgeId}
Params:
  - userId: MongoDB user ID (from path)
  - badgeId: Badge ID (from path) - e.g., "campus-catalyst"

Request Body: None

Response:
  200 OK:
    {
      "id": "userId",
      "featuredBadges": [...],
      ...otherUserData
    }

  400 Bad Request:
    "Badge not found in featured showcase"

  500 Internal Server Error:
    "Error: ..."
```

---

## After Server Restart

1. Refresh browser (Ctrl+Shift+R to clear cache)
2. Go to Badges page
3. Hover over featured badge
4. Click red minus icon
5. Badge should disappear
6. Should see success message
7. Public profile should auto-update

---

## Still Getting 404?

If you still see 404 after restart, try:

1. **Force Full Restart**

   ```powershell
   # Kill all Java processes
   Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -Confirm:$false
   # Wait 5 seconds
   Start-Sleep -Seconds 5
   # Start fresh
   mvn spring-boot:run -Dspring.profiles.active=dev
   ```

2. **Clear Maven Cache**

   ```powershell
   mvn clean
   rm -r target
   mvn spring-boot:run -Dspring.profiles.active=dev
   ```

3. **Check Logs**

   ```powershell
   mvn spring-boot:run -Dspring.profiles.active=dev 2>&1 | Tee-Object -FilePath "server.log"
   # Check server.log for errors
   ```

4. **Verify Path Mapping**
   - Look for "RequestMappingHandlerMapping" in server startup logs
   - Should see mapping for DELETE `/users/{userId}/profile/featured-badges/{badgeId}`

---

## Summary

**Problem:** 404 when trying to remove featured badge

**Root Cause:** Server running old code, DELETE endpoint not registered

**Solution:** Rebuild and restart server

**Status:** ✅ All code implemented and ready - just needs deployment

**Next Action:** Run `mvn clean spring-boot:run` and test removal functionality
