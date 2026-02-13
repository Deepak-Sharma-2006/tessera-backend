@echo off
REM ==========================================
REM Tessera Deployment Setup Script (Windows)
REM ==========================================
REM This script helps set up environment variables and deploy locally/to Render

REM ==========================================
REM Step 1: Get MongoDB Connection String
REM ==========================================
echo.
echo ======================================
echo Step 1: MongoDB Atlas Configuration
echo ======================================
echo.
echo Go to: https://cloud.mongodb.com
echo 1. Click "Clusters"
echo 2. Click "Connect" on your cluster
echo 3. Choose "Drivers" (Java)
echo 4. Copy the connection string
echo.
echo It should look like:
echo mongodb+srv://USERNAME:PASSWORD@cluster.mongodb.net/studencollabfin
echo.
set /p MONGODB_URI="Enter your MONGODB_URI: "

REM Validate MongoDB URI
if not defined MONGODB_URI (
    echo ERROR: MONGODB_URI is required!
    exit /b 1
)

if not "%MONGODB_URI:mongodb+srv://=%"=="%MONGODB_URI%" (
    echo.
    echo [OK] MONGODB_URI looks valid (contains mongodb+srv://)
) else if not "%MONGODB_URI:mongodb://=%"=="%MONGODB_URI%" (
    echo.
    echo [OK] MONGODB_URI looks valid (contains mongodb://)
) else (
    echo.
    echo ERROR: MONGODB_URI must start with mongodb:// or mongodb+srv://
    exit /b 1
)

REM ==========================================
REM Step 2: Generate JWT Secret
REM ==========================================
echo.
echo ======================================
echo Step 2: JWT Secret Configuration
echo ======================================
echo.
echo A random JWT secret is recommended (min 32 characters)
set /p JWT_SECRET="Enter JWT_SECRET (or press Enter for default): "

if not defined JWT_SECRET (
    set JWT_SECRET=StudCollabDefault2026
    echo Using default JWT_SECRET
)

REM ==========================================
REM Step 3: Set Environment Variables
REM ==========================================
echo.
echo ======================================
echo Step 3: Setting Environment Variables
echo ======================================
echo.
setlocal enabledelayedexpansion
set "MONGODB_URI=%MONGODB_URI%"
set "JWT_SECRET=%JWT_SECRET%"
set "SPRING_PROFILES_ACTIVE=dev"

echo.
echo [OK] Environment variables set:
echo MONGODB_URI: %MONGODB_URI:~0,50%...
echo JWT_SECRET: %JWT_SECRET:~0,20%...
echo SPRING_PROFILES_ACTIVE: %SPRING_PROFILES_ACTIVE%

REM ==========================================
REM Step 4: Build Backend
REM ==========================================
echo.
echo ======================================
echo Step 4: Building Backend (Java/Maven)
echo ======================================
echo.
cd /d d:\tessera\server

echo Cleaning previous builds...
call mvn clean

echo.
echo Running Maven install (this may take 1-2 minutes)...
call mvn install -DskipTests=true

if errorlevel 1 (
    echo.
    echo ERROR: Maven build failed!
    echo Please check the error messages above
    exit /b 1
)

echo.
echo [OK] Backend build successful!

REM ==========================================
REM Step 5: Test Local MongoDB Connection
REM ==========================================
echo.
echo ======================================
echo Step 5: Testing MongoDB Connection
echo ======================================
echo.
echo Starting Spring Boot server in 2 seconds...
echo Press Ctrl+C to stop the server
echo.
timeout /t 2

start "Tessera Backend" cmd /k "cd /d d:\tessera\server && mvn spring-boot:run"

echo.
echo ======================================
echo [OK] Server starting in new window
echo ======================================
echo.
echo Expected output:
echo   "Connected to MongoDB"
echo   "Server started on port 8080"
echo.
echo Test at: http://localhost:8080/api/health
echo.
echo Press any key to continue with frontend setup...
pause

REM ==========================================
REM Step 6: Build Frontend
REM ==========================================
echo.
echo ======================================
echo Step 6: Building Frontend (React)
echo ======================================
echo.
cd /d d:\tessera\client

echo.
echo Installing dependencies...
call npm install

echo.
echo Building React app...
call npm run build

if errorlevel 1 (
    echo.
    echo ERROR: Frontend build failed!
    exit /b 1
)

echo.
echo [OK] Frontend built successfully!

REM ==========================================
REM Step 7: Git Commit & Push
REM ==========================================
echo.
echo ======================================
echo Step 7: Git Commit & Push to GitHub
echo ======================================
echo.
cd /d d:\tessera

echo.
echo Checking git status...
git status

echo.
set /p CONTINUE="Continue with git push? (y/n): "
if /i "%CONTINUE%"=="y" (
    echo.
    echo Adding changes...
    git add .
    
    echo.
    echo Committing changes...
    git commit -m "feat: Badge system deployment - Bridge Builder, Pod Pioneer, Featured Badges, Real-time unlocks"
    
    echo.
    echo Pushing to GitHub...
    git push origin main
    
    echo.
    echo [OK] Changes pushed to GitHub!
    echo.
    echo Render.com will auto-deploy in 1-2 minutes
) else (
    echo.
    echo Skipped git push
)

REM ==========================================
REM Step 8: Setup Render Environment Variables
REM ==========================================
echo.
echo ======================================
echo Step 8: Setup Render.com Environment
echo ======================================
echo.
echo Go to: https://dashboard.render.com
echo.
echo 1. Select your service: tessera-backend
echo 2. Go to: Settings ^> Environment
echo 3. Add/Update these variables:
echo.
echo    MONGODB_URI = %MONGODB_URI:~0,50%...
echo    JWT_SECRET = %JWT_SECRET:~0,30%...
echo    SPRING_PROFILES_ACTIVE = prod
echo.
echo 4. Click "Save"
echo 5. Click "Manual Deploy" ^> "Deploy Latest Commit"
echo.
echo Render will restart and use your MongoDB Atlas connection!
echo.
pause

echo.
echo ======================================
echo [SUCCESS] Deployment Setup Complete!
echo ======================================
echo.
echo Next Steps:
echo 1. Wait for Render deployment to finish
echo 2. Test at: https://tessera-backend.onrender.com/api/health
echo 3. Test features in your app
echo.
pause
