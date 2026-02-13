@echo off
REM Quick Start Script for Tessera Development
REM This script starts MongoDB using Docker Compose and the Spring Boot application

setlocal enabledelayedexpansion

echo.
echo ========================================
echo Tessera Development Environment Setup
echo ========================================
echo.

REM Check if Docker is installed
docker --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not installed or not in PATH
    echo Please install Docker Desktop: https://www.docker.com/products/docker-desktop
    pause
    exit /b 1
)

echo [OK] Docker is installed
echo.

REM Start MongoDB
echo ========================================
echo Starting MongoDB Container...
echo ========================================
docker-compose up -d

if errorlevel 1 (
    echo [ERROR] Failed to start MongoDB container
    pause
    exit /b 1
)

echo [OK] MongoDB started successfully
echo.
echo MongoDB is running at: mongodb://admin:tessera_dev_pass@localhost:27017
echo MongoDB Express GUI: http://localhost:8081
echo.

REM Wait for MongoDB to be ready
echo Waiting for MongoDB to be ready (30 seconds)...
timeout /t 30 /nobreak

echo.
echo ========================================
echo Building Spring Boot Application...
echo ========================================
cd server
call mvn clean package -DskipTests

if errorlevel 1 (
    echo [ERROR] Maven build failed
    cd ..
    pause
    exit /b 1
)

echo [OK] Build successful
echo.

echo ========================================
echo Starting Spring Boot Application...
echo ========================================
echo Application will run at: http://localhost:8080
echo.
call mvn spring-boot:run -Dspring.profiles.active=dev

if errorlevel 1 (
    echo [ERROR] Application failed to start
    cd ..
    pause
    exit /b 1
)

cd ..
pause
