# Final Deployment Steps for Tessara

## Status: Ready for Production Deployment ✅

All code changes have been compiled successfully and pushed to GitHub.

---

## Step 1: Verify Code is in GitHub

Your latest changes have been pushed:

```
Commit: 9d810d6 "fix: Recreate production properties file for Render Atlas deployment"
Branch: main
Status: up-to-date with origin/main
```

**Verify:** Go to https://github.com/Deepak-Sharma-2006/tessera-backend and see the latest commits.

---

## Step 2: Get Friend's MongoDB Atlas Credentials

You need the following information from your friend's MongoDB Atlas cluster:

```
MongoDB Connection String Format:
mongodb+srv://USERNAME:PASSWORD@cluster-name.mongodb.net/studencollabfin
```

**Example Format (Replace with your actual credentials):**

```
mongodb+srv://YOUR_MONGODB_USERNAME:YOUR_MONGODB_PASSWORD@your-cluster.mongodb.net/studencollabfin
```

**Where to get this:**

1. Go to https://cloud.mongodb.com
2. Log in with your friend's credentials
3. Go to Database → Connect → Connect your application
4. Copy the full connection string with username and password

---

## Step 3: Configure Environment Variables on Render

Your app is already deployed at: **https://tessera-backend.onrender.com**

To update it with the new code and MongoDB Atlas:

### 3a. Go to Render Dashboard

1. Log in to https://dashboard.render.com
2. Click on your "tessera" backend service
3. Click "Settings"

### 3b. Update Environment Variables (Environment tab)

Replace or add these variables:

| Variable Name            | Value                                                         | Notes                                                    |
| ------------------------ | ------------------------------------------------------------- | -------------------------------------------------------- |
| `MONGODB_URI`            | `mongodb+srv://user:pass@cluster.mongodb.net/studencollabfin` | From friend's Atlas - **MUST include username:password** |
| `JWT_SECRET`             | Random 32+ character string                                   | Can run: `openssl rand -base64 32` on your terminal      |
| `SPRING_PROFILES_ACTIVE` | `prod`                                                        | Activates production config                              |
| `PORT`                   | `8080`                                                        | Keep as default                                          |

### 3c. Save Environment Variables

Click "Save changes"

---

## Step 4: Trigger Redeployment

After saving environment variables:

1. **Option A:** GitHub Push Trigger (Recommended)
   - Push a new commit to trigger auto-deployment:
     ```bash
     cd d:\tessera
     git add .
     git commit -m "chore: Trigger deployment with Atlas"
     git push origin main
     ```
   - Go to Render Dashboard → select service → see "Deploying..." status
   - Wait 2-3 minutes for deployment to complete

2. **Option B:** Manual Redeployment
   - In Render Dashboard: Click "Manual Deploy" → "Deploy latest"
   - Wait for deployment to complete

---

## Step 5: Monitor Deployment Logs

1. In Render Dashboard, click on your service
2. Click "Logs" tab
3. Look for these success indicators:

### ✅ Successful Connection Signs:

```
[INFO ] ... Attempting connection to mongodb+srv://...
[INFO ] ... Connection to MongoDB Atlas succeeded
[INFO ] ... Spring Boot 3.2.5 application started
[INFO ] ... Application listening on port 8080
```

### ❌ Common Error Signs:

```
[ERROR] Connection refused - Check MONGODB_URI
[ERROR] Invalid connection string - Verify format
[ERROR] Authentication failed - Check username/password
```

If you see errors, double-check the connection string from Step 2.

---

## Step 6: Test Deployment

Once deployment completes successfully:

### 6a. Test Backend Health

```bash
curl https://tessera-backend.onrender.com/api/health
# Expected: 200 OK or similar success response
```

### 6b. Test Key Features

1. **Login**
   - Go to frontend (https://tessera.onrender.com or http://localhost:3000)
   - Log in with test account
   - Should fetch user profile with badges

2. **Bridge Builder Badge** (Inter-college messaging)
   - Send a message to someone from a different college domain
   - Badge should unlock immediately in BadgeCenter
   - Check browser console for WebSocket messages

3. **Pod Pioneer Badge** (Pod joining)
   - Join a collaboration pod
   - Badge should unlock in BadgeCenter within seconds
   - Check console for unlocked message

4. **Event Creation** (Feature gating)
   - Try to create an event
   - If you don't have required badges → 403 Forbidden
   - If you have badges (Founding Dev or Campus Catalyst) → Success

5. **MongoDB Data**
   - Log in to friend's MongoDB Atlas
   - Go to Collections → achievements
   - Should see new badge records being created when you trigger unlocks

---

## Step 7: Verify Everything Works

### ✅ Checklist:

- [ ] Backend deployment shows "Live" status on Render
- [ ] Logs show successful MongoDB connection
- [ ] Health endpoint returns success
- [ ] Login works and displays user data
- [ ] Badge unlocks trigger in real-time
- [ ] Event creation button appears (with proper badges)
- [ ] MongoDB Atlas shows new data entries

---

## Troubleshooting

### Issue: "Connection string is invalid"

**Solution:**

- Ensure MONGODB_URI starts with `mongodb+srv://`
- Include username and password
- Format: `mongodb+srv://USER:PASS@cluster.mongodb.net/studencollabfin`

### Issue: "Authentication failed"

**Solution:**

- Verify username and password are correct
- Check friend's MongoDB Atlas console for IP whitelist
- May need to add "0.0.0.0/0" to allow Render's servers

### Issue: Badges not unlocking in frontend

**Solution:**

- Check browser console for WebSocket errors
- Verify `/ws` endpoint is accessible
- Check backend logs for "Attempting to broadcast badge unlock"

### Issue: Deployment stuck on "Building"

**Solution:**

- Wait up to 5 minutes (Render can be slow)
- Check logs for actual errors
- If compilation error appears, code needs fixes

### Issue: Old code still running

**Solution:**

- Force redeploy: Click "Clear Build Cache" then "Manual Deploy"
- Verify commit hash in Render logs matches your latest GitHub commit

---

## Important Notes

⚠️ **CRITICAL:**

1. MONGODB_URI must be set BEFORE app fully starts
2. Application will still start with fallback if not set (uses local MongoDB)
3. Friend should NOT use same password for multiple services
4. Keep JWT_SECRET secret - never commit it to GitHub

📝 **Documentation Files Created:**

- `SECURITY_AUDIT_REPORT.md` - Details about credential removal
- `DEPLOYMENT_CHECKLIST.md` - Step-by-step verification
- `application-prod.properties` - Production Spring Boot config

🚀 **All four badges are fully implemented:**

- ✅ Founding Dev (when isDev=true)
- ✅ Campus Catalyst (when role=COLLEGE_HEAD)
- ✅ Bridge Builder (when sending inter-college messages)
- ✅ Pod Pioneer (when joining your first pod)

---

## Need Help?

If deployment fails:

1. Check Render logs for specific error message
2. Verify MONGODB_URI format is exactly right
3. Verify MongoDB Atlas IP whitelist includes Render servers
4. Verify JWT_SECRET is set (at least 8 characters)
5. Try manual redeployment with clear cache

**Current Status:**

- Code: ✅ Compiled successfully
- GitHub: ✅ Pushed successfully
- Render: ⏳ Awaiting environment variables and redeployment
- MongoDB: ⏳ Waiting for your friend's Atlas credentials

You're ready to deploy! 🚀
