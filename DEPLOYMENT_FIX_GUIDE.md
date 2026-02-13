# MongoDB Deployment Fix - Complete Resolution Guide

## Summary of Changes Made

I've resolved your MongoDB connection errors by implementing the following:

### 1. **Configuration Files Updated**

- ✅ `application.properties` - Added proper MongoDB connection timeout and UUID config
- ✅ `application-dev.properties` - Configured for local Docker MongoDB or native installation
- ✅ `application-prod.properties` - Already configured for MongoDB Atlas (production)

### 2. **Docker Support Added**

- ✅ `docker-compose.yml` - Complete MongoDB setup with Docker Compose
- ✅ `.env` - Environment variables for MongoDB credentials
- ✅ `start-dev.bat` - Windows batch script to start everything with one click

### 3. **Documentation Created**

- ✅ `MONGODB_SETUP_GUIDE.md` - Complete setup guide with 3 options

### 4. **Build Status**

- ✅ Maven build: **SUCCESS**
- ✅ Application JAR created: `target/server-0.0.1-SNAPSHOT.jar`

---

## Quick Start - Choose Your Option

### **OPTION A: Start with Docker (EASIEST - Recommended)**

1. **Install Docker Desktop** (if not already installed)
   - Download: https://www.docker.com/products/docker-desktop
   - Default settings are fine

2. **Start MongoDB with one command:**

   ```bash
   cd d:\tessera
   docker-compose up -d
   ```

3. **Build and start your application:**

   ```bash
   cd d:\tessera\server
   mvn spring-boot:run -Dspring.profiles.active=dev
   ```

4. **Access your app:**
   - Backend: http://localhost:8080
   - MongoDB GUI: http://localhost:8081 (username: admin, password: tessera_dev_pass)

5. **When done, stop MongoDB:**
   ```bash
   cd d:\tessera
   docker-compose down
   ```

**Or use the one-click batch file (after installing Docker):**

```bash
cd d:\tessera
.\start-dev.bat
```

---

### **OPTION B: Use MongoDB Atlas (BEST FOR PRODUCTION)**

1. **Sign up for free MongoDB Atlas account:**
   - Visit: https://www.mongodb.com/cloud/atlas/register
   - Create free M0 cluster

2. **Get your connection string:**
   - Databases → Your Cluster → Connect → Drivers
   - Copy the connection string (format: `mongodb+srv://username:password@cluster.mongodb.net/studencollabfin`)

3. **Test locally with environment variable:**

   ```bash
   set MONGODB_URI=mongodb+srv://your_username:your_password@cluster.mongodb.net/studencollabfin
   cd d:\tessera\server
   mvn spring-boot:run -Dspring.profiles.active=prod
   ```

4. **Whitelist your IP in MongoDB Atlas:**
   - Security → Network Access → Add IP Address
   - Add your current IP (or 0.0.0.0/0 for testing only)

---

## Deployment to Render - Complete Checklist

### Prerequisites

- ✅ Maven build is successful
- ✅ GitHub repository is up to date

### Steps:

1. **Push your latest code:**

   ```bash
   cd d:\tessera
   git add .
   git commit -m "MongoDB configuration fixes"
   git push origin main
   ```

2. **On Render Dashboard:**
   - Go to your `tessera-backend` service
   - Click "Settings"
   - Scroll to "Environment"
   - Add/Update these variables:
     ```
     MONGODB_URI = mongodb+srv://username:password@cluster.mongodb.net/studencollabfin
     JWT_SECRET = (generate a random 32+ character string)
     SPRING_PROFILES_ACTIVE = prod
     PORT = 10000
     ```

   **Generate a strong JWT_SECRET:**

   ```bash
   # In PowerShell:
   [Convert]::ToBase64String([System.Security.Cryptography.RNGCryptoServiceProvider]::Create().GetBytes(32))
   ```

3. **Redeploy:**
   - Click "Redeploy latest commit"
   - Wait for deployment to complete

4. **Verify Deployment:**
   - Check logs for: "Mongodb driver version"
   - No timeout or connection errors
   - Response poll returns successful data

---

## Why These Changes Fix Your Errors

| Error                                              | Root Cause                                         | Fix                                            |
| -------------------------------------------------- | -------------------------------------------------- | ---------------------------------------------- |
| `Connection refused localhost:27017`               | MongoDB not running locally                        | Use Docker Compose or native MongoDB           |
| `MongoTimeoutException: Timed out after 30000 ms`  | Connection string incorrect or MongoDB unavailable | Use Atlas connection string or start container |
| `Cannot resolve reference to bean 'mongoTemplate'` | MongoDB connection failed during startup           | Above fixes resolve this cascade error         |

---

## Verification Checklist

After starting with your chosen method, verify:

- [ ] No connection timeout errors in logs
- [ ] Application starts successfully (port 8080 shows no errors)
- [ ] No "Cannot resolve reference to bean 'mongoTemplate'" errors
- [ ] Can access http://localhost:8080 without errors
- [ ] If using Docker Compose: MongoDB Express accessible at http://localhost:8081

---

## Troubleshooting

### Docker Issues

```bash
# Check if Docker is running
docker ps

# View Docker Compose logs
docker-compose logs mongodb

# Rebuild everything
docker-compose down  # Stop
docker-compose up -d # Start fresh
```

### MongoDB Atlas Issues

```bash
# Test connection locally
set MONGODB_URI=mongodb+srv://user:pass@cluster.mongodb.net/studencollabfin
mvn spring-boot:run -Dspring.profiles.active=prod
```

### Port Already in Use

```bash
# Find process using port 8080
netstat -ano | findstr :8080

# Kill the process (replace PID with actual process ID)
taskkill /PID [PID] /F
```

---

## Next Steps

1. **Choose your MongoDB deployment method** (Docker Compose or Atlas recommended)
2. **Follow the Quick Start section** above
3. **Test locally** before pushing to Render
4. **Update Render environment variables** with correct MONGODB_URI
5. **Redeploy** on Render

Your build is ready - **now just add MongoDB and you're deployed!**
