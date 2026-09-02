@echo off
setlocal
title PhoneLocation Setup

set PKG=com.thatscodeguy.phonelocation
set API_BASE=https://phone-location-script.vercel.app
set APK=%~dp0PhoneLocation.apk

echo ============================================================
echo   PhoneLocation one-click setup
echo   Phone: Developer options + USB debugging ON
echo ============================================================
echo.

where adb >nul 2>nul
if errorlevel 1 (
  echo [ERROR] adb not found. Copy adb.exe and ALL platform-tools files into this folder.
  pause
  exit /b 1
)

if not exist "%APK%" (
  echo [ERROR] PhoneLocation.apk not found in this folder. Rename your APK file to PhoneLocation.apk
  pause
  exit /b 1
)

set /p DEVICE_KEY=Paste DEVICE_KEY here: 
if "%DEVICE_KEY%"=="" (
  echo [ERROR] DEVICE_KEY is required
  pause
  exit /b 1
)

echo.
echo [1/7] Checking device...
adb get-state >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Device not found. Check cable / USB debugging / allow the popup on phone screen.
  pause
  exit /b 1
)

echo [2/7] Installing APK...
adb install -r "%APK%"
if errorlevel 1 (
  echo [ERROR] Install failed.
  pause
  exit /b 1
)

echo [3/7] Granting location permissions...
adb shell pm grant %PKG% android.permission.ACCESS_FINE_LOCATION
adb shell pm grant %PKG% android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant %PKG% android.permission.ACCESS_BACKGROUND_LOCATION

echo [4/7] Granting notification permission...
adb shell pm grant %PKG% android.permission.POST_NOTIFICATIONS

echo [5/7] Exact alarm + battery whitelist...
adb shell appops set %PKG% SCHEDULE_EXACT_ALARM allow
adb shell dumpsys deviceidle whitelist +%PKG%

echo [6/7] Writing config and starting service...
adb shell am start -n %PKG%/.MainActivity --es api_base "%API_BASE%" --es device_key "%DEVICE_KEY%" --es device_name "primary" --ei passive_min 60

echo [7/7] Setting device owner (anti-uninstall / anti-force-stop)...
adb shell dpm set-device-owner --user 0 %PKG%/.DeviceAdmin
if errorlevel 1 (
  echo   [NOTE] Not fatal. It only means after reboot you must open the app once manually.
  echo          Common cause: Mi account logged in. Log out all accounts then rerun this script.
)

echo.
echo ============================================================
echo   DONE! Open the map to verify:
echo   %API_BASE%/map?token=YOUR_ACCESS_TOKEN
echo ============================================================
pause
