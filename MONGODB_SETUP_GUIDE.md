# MongoDB Setup Guide for Tessera Deployment

## Problem

Your application fails to start because MongoDB is not available at `localhost:27017`. This guide provides multiple solutions based on your deployment scenario.

---

## Option 1: Local Development with Docker Compose (RECOMMENDED)

### Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop) installed and running

### Setup Steps

1. **Start MongoDB using Docker Compose:**

   ```bash
   cd d:\tessera
   docker-compose up -d
   ```

   This will start:
   - **MongoDB** on `localhost:27017` with credentials `admin:tessera_dev_pass`
   - **MongoDB Express** (GUI) on `http://localhost:8081` (optional, for easy management)

2. **Verify MongoDB is running:**

   ```bash
   docker ps
   ```

   You should see `tessera-mongodb` and `tessera-mongo-express` containers running.

3. **Build and start the application:**

   ```bash
   cd d:\tessera\server
   mvn clean package
   mvn spring-boot:run -Dspring.profiles.active=dev
   ```

4. **Access the application:**
   - Backend: `http://localhost:8080`
   - MongoDB Express: `http://localhost:8081`

5. **Stop MongoDB when done:**
   ```bash
   docker-compose down
   ```

---

## Option 2: Local MongoDB (No Docker)

If you prefer running MongoDB natively on Windows:

### Setup Steps

1. **Download and Install MongoDB Community Edition**
   - Visit: https://www.mongodb.com/try/download/community
   - Select Windows and download the MSI installer
   - Run the installer and choose "Install as a Service"

2. **Start MongoDB:**
   - MongoDB will be running as a Windows service after installation
   - Verify: Open Command Prompt and run:
     ```bash
     mongosh localhost:27017
     ```

3. **Create admin user (optional but recommended):**

   ```javascript
   // In mongosh:
   use admin
   db.createUser({
     user: "admin",
     pwd: "tessera_dev_pass",
     roles: ["root"]
   })
   ```

4. **Update application-dev.properties:**
   If you didn't create the admin user, change:

   ```properties
   spring.data.mongodb.uri=mongodb://localhost:27017/studencollabfin
   ```

5. **Build and start the application:**
   ```bash
   cd d:\tessera\server
   mvn clean package
   mvn spring-boot:run -Dspring.profiles.active=dev
   ```

---

## Option 3: MongoDB Atlas (Production/Cloud)

This is the **RECOMMENDED approach for production deployment.**

### Setup Steps

1. **Create MongoDB Atlas Account**
   - Visit: https://www.mongodb.com/cloud/atlas
   - Sign up for a free M0 cluster

2. **Create a Cluster**
   - Choose the free tier
   - Select your preferred region (closest to your users)
   - Wait for cluster to be ready (~5 minutes)

3. **Create Database User**
   - Go to "Security" → "Database Access"
   - Click "Add New Database User"
   - Create username and password (store these securely)
   - Grant roles: "Atlas Admin"

4. **Get Connection String**
   - Go to "Databases" → Your cluster → "Connect"
   - Click "Drivers"
   - Copy the connection string
   - Example: `mongodb+srv://username:password@cluster.mongodb.net/studencollabfin`

5. **Set Environment Variable**

   **For Local Testing:**

   ```bash
   set MONGODB_URI=mongodb+srv://username:password@cluster.mongodb.net/studencollabfin
   cd d:\tessera\server
   mvn clean package
   mvn spring-boot:run -Dspring.profiles.active=prod
   ```

   **For Render Deployment:**
   - Go to your Render service
   - Settings → Environment Variables
   - Add: `MONGODB_URI` = `mongodb+srv://username:password@cluster.mongodb.net/studencollabfin`
   - Redeploy

6. **Whitelist IP Addresses (Important!)**
   - Go to Atlas → "Security" → "Network Access"
   - Add your IP address (or "0.0.0.0/0" for development only)

---

## Testing Your Setup

After completing any of the above options, test your connection:

```bash
cd d:\tessera\server
mvn spring-boot:run -Dspring.profiles.active=dev
```

**Success indicators:**

- No timeout errors in logs
- Application starts successfully
- Logs show: `Mongodb driver version: x.x.x`
- You can navigate to `http://localhost:8080` without errors

---

## Troubleshooting

### Error: "Connection refused"

- **Docker Compose**: Run `docker-compose up` in the tessera directory
- **Native**: Check if MongoDB service is running: `net start MongoDB` (Windows)
- **Atlas**: Verify IP whitelist includes your current IP

### Error: "Authentication failed"

- Verify username/password in connection string
- For Docker: Default is `admin:tessera_dev_pass`

### Error: "Database not found"

- This is OK - Spring Data MongoDB will create it automatically
- Check logs for `auto-index-creation=true` confirmation

### Logs show connection attempts but app keeps running

- App will retry for 30 seconds
- If MongoDB comes online during this window, it will connect
- Otherwise startup fails (as expected)

---

## Deployment Checklist

### For Render Production Deployment:

- [ ] Use MongoDB Atlas (not localhost)
- [ ] Set `MONGODB_URI` environment variable on Render
- [ ] Set `JWT_SECRET` environment variable on Render
- [ ] Whitelist Render IP addresses in MongoDB Atlas
- [ ] Set `SPRING_PROFILES_ACTIVE=prod` on Render
- [ ] Test deployment with `mvn spring-boot:run -Dspring.profiles.active=prod` locally first

### For Local Development:

- [ ] Either run `docker-compose up` OR install native MongoDB
- [ ] Run with `mvn spring-boot:run -Dspring.profiles.active=dev`
- [ ] Can use MongoDB Express for GUI management (via Docker Compose)
