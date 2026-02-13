# Render Deployment - Production Configuration Checklist

## ✅ Code Status

- **Latest Commit:** `fix: remove localhost dependency - require MongoDB URI environment variable for production`
- **Repository:** https://github.com/Deepak-Sharma-2006/tessera-backend
- **Branch:** main
- **Build Status:** ✅ SUCCESS

---

## 🚀 Render Deployment Requirements

### **REQUIRED Environment Variables**

You MUST set these variables in your Render service before deployment:

| Variable                 | Value                                                                 | Notes                                                                                                  |
| ------------------------ | --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| `MONGODB_URI`            | `mongodb+srv://username:password@cluster.mongodb.net/studencollabfin` | **CRITICAL** - Must use MongoDB Atlas (not localhost). This is the only MongoDB option for production. |
| `JWT_SECRET`             | Random 32+ character string                                           | Generate a secure token, e.g., using: `openssl rand -base64 32`                                        |
| `SPRING_PROFILES_ACTIVE` | `prod`                                                                | Activates production configuration profile                                                             |
| `PORT`                   | `10000`                                                               | Render-assigned port (usually 10000)                                                                   |

---

## 🔧 Setup Steps on Render Dashboard

1. **Go to your tessera-backend service**
2. **Click "Settings"**
3. **Scroll to "Environment"**
4. **Add/Update these environment variables:**

```
MONGODB_URI = mongodb+srv://your_username:your_password@your_cluster.mongodb.net/studencollabfin
JWT_SECRET = [Generate random 32+ char string]
SPRING_PROFILES_ACTIVE = prod
PORT = 10000
```

5. **Save changes**
6. **Click "Redeploy latest commit"**
7. **Wait for deployment to complete** (2-5 minutes)

---

## 📋 Configuration Details

### MongoDB Atlas Setup

- **Cluster:** Use any MongoDB Atlas free tier cluster
- **Connection String Format:** `mongodb+srv://username:password@cluster.mongodb.net/studencollabfin`
- **Network Access:** Whitelist Render IP addresses
  - Go to MongoDB Atlas → Security → Network Access
  - Add IP: `0.0.0.0/0` for testing (not recommended for production)
  - Or whitelist specific Render IPs

### Application Configuration:

- **application.properties** → Requires MONGODB_URI environment variable (no localhost fallback)
- **application-prod.properties** → Production-specific settings
- **No localhost references** → Safe for production deployment

---

## ⚠️ Important Notes

### What Was Changed:

- ❌ **REMOVED:** `mongodb://localhost:27017` fallback
- ✅ **REQUIRES:** `MONGODB_URI` environment variable must be set
- ✅ **ADDED:** Production-safe configuration with timeouts

### Why This Prevents Errors:

- Application will NOT try to connect to localhost
- If MONGODB_URI is not set, app will fail fast with clear error (better than hanging)
- Uses MongoDB Atlas for cloud-based database (enterprise-grade)

---

## 🧪 Testing Before Deployment

If you want to test locally with the production configuration:

```bash
set MONGODB_URI=mongodb+srv://your_username:your_password@your_cluster.mongodb.net/studencollabfin
set JWT_SECRET=your-secret-key-here
set SPRING_PROFILES_ACTIVE=prod
cd d:\tessera\server
mvn spring-boot:run
```

---

## ✅ Deployment Verification

After deployment, verify success by checking:

1. ✅ No timeout errors in logs
2. ✅ Application started successfully
3. ✅ Health check endpoint responds: `GET https://your-app.onrender.com/api/health`
4. ✅ No "Cannot resolve reference to bean 'mongoTemplate'" errors
5. ✅ No "Connection refused" errors

---

## 🆘 Troubleshooting Guides

### Error: "MONGODB_URI not found"

- **Solution:** Add `MONGODB_URI` environment variable to Render dashboard
- **Verify:** Restart/redeploy service

### Error: "Connection timeout after 30000 ms"

- **Solution:** Check MongoDB Atlas network access whitelist
- **Verify:** IP is whitelisted in Atlas → Security → Network Access

### Error: "Authentication failed"

- **Solution:** Verify username/password in connection string
- **Format:** `mongodb+srv://USERNAME:PASSWORD@cluster.mongodb.net/database`
- **Note:** Special characters in password should be URL-encoded

### Error: "Cannot connect to writable server"

- **Solution:** MongoDB Atlas might be provisioning cluster
- **Action:** Wait 5 minutes and retry deployment

---

## 📞 Quick Commands

**Generate strong JWT_SECRET (copy the output):**

```bash
# PowerShell:
[Convert]::ToBase64String([System.Security.Cryptography.RNGCryptoServiceProvider]::Create().GetBytes(32))

# Or bash:
openssl rand -base64 32
```

**Test MongoDB connection locally:**

```bash
set MONGODB_URI=mongodb+srv://your_user:your_pass@cluster.mongodb.net/studencollabfin
cd d:\tessera\server
mvn spring-boot:run -Dspring.profiles.active=prod
```

---

## 📌 Summary

Your code is now **production-ready**:

- ✅ No localhost dependency
- ✅ All configuration via environment variables
- ✅ Latest code pushed to GitHub main branch
- ✅ Build succeeds without errors

**Next Step:** Set the 4 environment variables on Render and redeploy!
