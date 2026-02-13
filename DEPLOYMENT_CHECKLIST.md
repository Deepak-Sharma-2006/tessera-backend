# Deployment Checklist - Tessera with Friend's MongoDB Atlas

**Goal:** Deploy Tessera to Render.com using your friend's MongoDB Atlas cluster

---

## Pre-Deployment (Local Testing)

### ✅ Step 1: Clean Maven Build

```bash
cd d:\tessera\server
mvn clean compile
```

**Expected Output:**

```
[INFO] BUILD SUCCESS
```

**If you see errors:**

- Run: `mvn clean install`
- Rebuild: `mvn clean -DskipTests=true compile`

### ✅ Step 2: Get Friend's MongoDB URI

Ask your friend for their **MongoDB Atlas Connection String**:

1. Friend logs into [MongoDB Atlas](https://cloud.mongodb.com)
2. Goes to: Clusters → Connect → Drivers
3. Gets connection string like:

   ```
   mongodb+srv://USERNAME:PASSWORD@cluster.mongodb.net/studencollabfin?retryWrites=true&w=majority
   ```

4. **IMPORTANT:** Make sure to:
   - Replace `<password>` with actual password if shown as placeholder
   - Keep the database name: `/studencollabfin`

### ✅ Step 3: Test Locally with Friend's Credentials

**Windows Command Prompt:**

```bash
set MONGODB_URI=mongodb+srv://USERNAME:PASSWORD@cluster.mongodb.net/studencollabfin
set JWT_SECRET=your-random-secret-key-min-32-characters
cd d:\tessera\server
mvn clean spring-boot:run -Dspring.profiles.active=dev
```

**Expected Output:**

```
[INFO] Tessera Backend Server is running on: http://localhost:8080
[INFO] MongoDB connection successful
```

**If connection fails:**

- Check password is correct (friend's password, not "PASSWORD")
- Check cluster name is correct
- Check database name is "studencollabfin"
- Ask friend to check Atlas Network Access settings (allow all IPs: 0.0.0.0/0)

### ✅ Step 4: Test Frontend

In another terminal:

```bash
cd d:\tessera\client
npm run dev
```

**Expected Output:**

```
VITE v7.0.4  ready in 123 ms
Local:    http://localhost:5173/
```

### ✅ Step 5: Test Features

1. **Login:**
   - Go to http://localhost:5173
   - Log in with test account
   - Should connect to friend's MongoDB

2. **Test Badge System:**
   - Check Badge Center loads
   - Check featured badges display
   - Check user.badges populated from MongoDB

3. **Test Event Creation:**
   - Try to create event (should work if isDev=true)
   - Check event saves to friend's MongoDB

4. **Test Inter-College Messages:**
   - Send message between users from different colleges
   - Check Bridge Builder badge unlocks
   - Verify badges in friend's MongoDB

---

## Deployment to Render.com

### ✅ Step 1: Create Render Service

1. Go to [Render.com](https://render.com)
2. Click "New +" → "Web Service"
3. Connect GitHub repository
4. Configure:
   - **Name:** tessera-backend
   - **Runtime:** Java
   - **Build Command:** `mvn clean -DskipTests=true compile`
   - **Start Command:** `java -jar target/server-0.0.1-SNAPSHOT.jar`

### ✅ Step 2: Set Environment Variables on Render

On Render dashboard, go to **Environment**:

```
MONGODB_URI = mongodb+srv://USERNAME:PASSWORD@cluster.mongodb.net/studencollabfin

JWT_SECRET = your-random-secret-key-minimum-32-characters

SPRING_PROFILES_ACTIVE = prod
```

**IMPORTANT:**

- Copy exact connection string from friend's Atlas
- Include the password
- Keep database name: `studencollabfin`
- Use random JWT secret (can use: `openssl rand -base64 32`)

### ✅ Step 3: Deploy Backend

1. Push code to GitHub:

   ```bash
   git add .
   git commit -m "Deploy with Atlas credentials via env vars"
   git push
   ```

2. Render will auto-deploy
3. Check logs for:
   ```
   ✓ Connected to MongoDB
   ✓ Server started on port 10000
   ```

### ✅ Step 4: Deploy Frontend

Frontend setup depends on where you host it:

**Option A: Netlify (Recommended)**

1. Log in to [Netlify](https://netlify.com)
2. Connect GitHub repository
3. Build settings:
   - Build command: `npm run build`
   - Publish directory: `dist`
4. Environment variables: (none needed)
5. Deploy

**Option B: Render**

1. Create second Render service (Web Service)
2. Use Node build process
3. No special config needed

### ✅ Step 5: Update Frontend API Endpoint

In `client/src/lib/api.js`:

**Development (localhost:5173):**

```javascript
const baseURL = "http://localhost:8080";
```

**Production (Render):**

```javascript
const baseURL = "https://tessera-backend.onrender.com"; // Replace with your Render URL
```

---

## Post-Deployment Verification

### ✅ Step 1: Check Backend is Running

1. Go to `https://tessera-backend.onrender.com/api/users` (or your Render URL)
2. Should return JSON error or user list (not 404)
3. NOT "Cannot GET" (that means server running)

### ✅ Step 2: Check MongoDB Connection

1. SSH into Render:

   ```bash
   render logs <service-id>
   ```

2. Look for:
   ```
   ✓ MongoDB connected
   ✓ Badges sync working
   ```

### ✅ Step 3: Test Features

1. **User Login:**
   - Frontend connects to Render backend
   - Backend queries friend's MongoDB
   - User data returns correctly

2. **Badge System:**
   - Create event as isDev user
   - Badge unlocks
   - Appears in friend's MongoDB

3. **Inter-College Messages:**
   - Send message between institutions
   - Bridge Builder triggers
   - MongoDB updates

### ✅ Step 4: Check Logs for Errors

If features don't work:

```bash
# On Render dashboard
Logs → Search for errors
```

Common issues:

- `MONGODB URI not set` → Set env var on Render
- `JWT_SECRET not found` → Set env var on Render
- `Connection refused` → Check MongoDB URI is correct
- `Authentication required` → Check username/password in URI

---

## Rollback Plan

If something goes wrong:

### Option 1: Quick Rollback

1. On Render, update environment variables back to previous values
2. Redeploy

### Option 2: Full Rollback

1. Either revert git commit
2. Push to GitHub
3. Render auto-deploys previous version

### Option 3: Emergency

1. Go to Render dashboard
2. Click "Retry Deploy" on previous deployment

---

## Security Checklist

Before going live:

- [ ] MONGODB_URI env var set on Render (NOT in code)
- [ ] JWT_SECRET env var set on Render (NOT in code)
- [ ] Code does NOT contain friend's credentials
- [ ] `application.properties` uses `${VARIABLE}` format
- [ ] No `.env` files committed to git
- [ ] Friend has enabled Network Access for Render IP (0.0.0.0/0 or specific IP)
- [ ] CORS enabled for production frontend URL
- [ ] All tests pass locally before deploying

---

## Environment Variable Quick Reference

### Local Development

```bash
set MONGODB_URI=mongodb://localhost:27017/tessera_dev
set JWT_SECRET=local-dev-secret-key
```

### Local Testing with Friend's Atlas

```bash
set MONGODB_URI=mongodb+srv://USERNAME:PASSWORD@cluster.mongodb.net/studencollabfin
set JWT_SECRET=your-secret
```

### Production (Render Dashboard)

```
MONGODB_URI=mongodb+srv://USERNAME:PASSWORD@cluster.mongodb.net/studencollabfin
JWT_SECRET=your-secret
SPRING_PROFILES_ACTIVE=prod
```

---

## Troubleshooting

| Issue                        | Solution                                                             |
| ---------------------------- | -------------------------------------------------------------------- |
| "MongoDB connection refused" | Check MONGODB_URI is correct, check friend's Network Access settings |
| "Authentication failed"      | Check username and password in URI                                   |
| "Database not found"         | Check database name is "studencollabfin"                             |
| "JWT validation failed"      | Check JWT_SECRET is set consistently                                 |
| "CORS error"                 | Update CORS origins in `SecurityConfig.java` for prod URL            |
| "Events not saving"          | Check friend's MongoDB has write permissions                         |
| "Badges not unlocking"       | Check badge unlock logic in AchievementService                       |

---

## Success Indicators

✅ You'll know it's working when:

1. Frontend loads at your Render/Netlify URL
2. Can log in with MongoDB user from friend's Atlas
3. Badge Center displays badges from MongoDB
4. Can create events if isDev=true
5. Inter-college messages unlock Bridge Builder badge
6. Pod join unlocks Pod Pioneer badge
7. All data persists in friend's MongoDB cluster
8. Mobile responsive and all features work

---

## Final Commands

After everything is set up:

**Build locally:**

```bash
mvn clean -DskipTests=true compile
```

**Test with friend's Atlas:**

```bash
set MONGODB_URI=YOUR_FRIEND'S_URI
set JWT_SECRET=YOUR_SECRET
mvn spring-boot:run
```

**Deploy to production:**

```bash
git push origin main
# Render auto-deploys!
```

---

**Status:** Ready for Production Deployment with Friend's MongoDB Atlas ✅
