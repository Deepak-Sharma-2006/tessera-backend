# Render Deployment - MongoDB URI Fix (CRITICAL)

## 🔴 The Error You're Seeing

```
Caused by: java.lang.IllegalArgumentException: The connection string is invalid.
Connection strings must start with either 'mongodb://' or 'mongodb+srv://
```

## ✅ Root Cause & Solution

The issue is **NOT** in the code. The issue is in your **Render Dashboard environment variables**.

Your `MONGODB_URI` variable is either:

1. ❌ Not set at all
2. ❌ Set with wrong prefix (missing `mongodb+srv://`)
3. ❌ Set with spaces or quotes around the URL
4. ❌ Set to a different variable name (like `MONGO_URI` instead of `MONGODB_URI`)

---

## 🚀 IMMEDIATE FIX - 3 Steps

### Step 1: Verify Your MongoDB Atlas Connection String

1. Go to **https://cloud.mongodb.com**
2. Click **"Databases"** → Select your cluster
3. Click **"Connect"**
4. Select **"Drivers"** (NOT "MongoDB Compass")
5. Choose **"Node.js"** as driver (doesn't matter which, we just want the string)
6. **Copy the connection string** (should look like):
   ```
   mongodb+srv://USERNAME:PASSWORD@cluster.mongodb.net/studencollabfin
   ```

**⚠️ IMPORTANT:** Replace `PASSWORD` with your actual Atlas password from your credentials.

---

### Step 2: Update Render Dashboard (THE FIX)

1. Go to **https://dashboard.render.com**
2. Select your **tessera-backend** service
3. Click **Settings**
4. Scroll to **Environment** section
5. **DELETE** any existing `MONGODB_URI` variable (if it exists)
6. Click **"Add Environment Variable"**

**Enter EXACTLY this:**

- **Key:** `MONGODB_URI`
- **Value:** `mongodb+srv://YOUR_USERNAME:YOUR_PASSWORD@your-cluster.region.mongodb.net/studencollabfin`

**CRITICAL CHECKLIST:**

- ✅ No spaces before `mongodb+srv://`
- ✅ No quotes around the URL `""`
- ✅ Replace `YOUR_PASSWORD_HERE` with actual Atlas password
- ✅ Key is exactly `MONGODB_URI` (not `MONGO_URI`, not `DATABASE_URL`)
- ✅ Value starts with exactly `mongodb+srv://`

7. Click **"Save"**
8. Render will auto-redeploy

---

### Step 3: Verify Deployment Success

1. Go to **Logs** in your Render service
2. Wait for redeploy to finish (~3-5 minutes)
3. **Look for this SUCCESS message:**

```
INFO 1 --- [main] org.mongodb.driver.cluster  : Monitor thread successfully
connected to server finiq.mukfozh.mongodb.net
```

If you see this, ✅ **Deployment successful!**

---

## 📋 What Changed in Code

**Before (Confusing):**

```properties
spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/studencollabfin}
```

❌ Problem: Confusing fallback, tries localhost if env var missing

**After (Fail-Proof):**

```properties
spring.data.mongodb.uri=${MONGODB_URI}
spring.data.mongodb.database=studencollabfin
```

✅ Solution: Requires `MONGODB_URI` - fails fast with clear error if missing

---

## 🆘 Troubleshooting

### Still Getting "Invalid Connection String" Error?

**Check 1: Is MONGODB_URI set on Render?**

```
Go to Settings → Environment
Search for "MONGODB_URI"
If it's not there or value is empty → ADD IT NOW
```

**Check 2: Does the value start with mongodb+srv://?**

```
❌ WRONG: username:password@cluster.mongodb.net/studencollabfin
❌ WRONG: "mongodb+srv://..." (with quotes)
❌ WRONG:  mongodb+srv://... (with space before)

✅ CORRECT: mongodb+srv://username:password@your-cluster.mongodb.net/studencollabfin
```

**Check 3: Is password correct?**

- Ask your friend for the exact MongoDB Atlas password
- Some special characters need URL encoding:
  - `@` → `%40`
  - `$` → `%24`
  - `/` → `%2F`

**Check 4: Redeploy after changes**

```
After updating environment variables on Render:
Settings → Redeploy Latest Commit
```

---

## 📞 Exact Connection String Format

```
mongodb+srv://USERNAME:PASSWORD@CLUSTER_NAME.REGION.mongodb.net/DATABASE_NAME
```

**Your values:**

- `USERNAME` = `<YOUR_MONGODB_USERNAME>`
- `PASSWORD` = `<YOUR_MONGODB_PASSWORD>` (ask your friend for the password they used for this user)
- `CLUSTER_NAME` = `<YOUR_CLUSTER_NAME>`
- `REGION` = `<YOUR_REGION>`
- `DATABASE_NAME` = `studencollabfin`

**Full example:**

```
mongodb+srv://YOUR_USERNAME:YOUR_PASSWORD@your-cluster.region.mongodb.net/studencollabfin
```

---

## ✅ Deployment Verification Checklist

After setting `MONGODB_URI` on Render and clicking redeploy:

- [ ] Render logs show "Deploy in progress..."
- [ ] No "Invalid connection string" error
- [ ] Logs show "Monitor thread successfully connected to server finiq.mukfozh.mongodb.net"
- [ ] No "Cannot resolve reference to bean 'mongoTemplate'" error
- [ ] Server is running (green status on Render dashboard)
- [ ] Can access http://your-render-url/api/health and get 200 OK

---

## 🎯 Summary

**The Code:** ✅ Already fixed and pushed to GitHub  
**The Problem:** The Render environment variable is wrong or missing  
**The Solution:** Add/fix `MONGODB_URI` on Render dashboard with correct Atlas URI

**Before Render Redeploy:**

1. Verify you have the correct MongoDB Atlas connection string
2. Go to Render dashboard
3. Set `MONGODB_URI` to the Atlas connection string
4. Delete any incorrect env vars
5. Save changes (auto-redeploy starts)
6. Monitor logs for success message

**Expected Timeline:**

- Code deployed: ✅ Done
- Env var updated: 5 minutes (you do this)
- Render redeploy: 3-5 minutes (automatic)
- App running: Total 10 minutes

You're this close! 🎯 Just fix the Render environment variable!
