@echo off
setlocal
chcp 65001 >nul
title PhoneLocation 一键装配

set PKG=com.thatscodeguy.phonelocation
set API_BASE=https://phone-location-script.vercel.app
set APK=%~dp0PhoneLocation.apk

echo ============================================================
echo   PhoneLocation 一键装配
echo   前置: 手机已打开 开发者选项 + USB 调试 并授权本电脑
echo ============================================================
echo.

where adb >nul 2>nul
if errorlevel 1 (
  echo [错误] 未找到 adb。
  echo        下载 Google Platform-Tools 解压后把 adb.exe 所在目录加入 PATH,
  echo        或把 adb.exe 放到本脚本同目录后重试。
  pause
  exit /b 1
)

if not exist "%APK%" (
  echo [错误] 未找到 %APK%
  echo        先从 GitHub Release 下载 APK, 放到本脚本同目录并命名为 PhoneLocation.apk
  pause
  exit /b 1
)

set /p DEVICE_KEY=输入 DEVICE_KEY (Vercel 环境变量里那串): 
if "%DEVICE_KEY%"=="" (
  echo [错误] DEVICE_KEY 不能为空
  pause
  exit /b 1
)

echo.
echo [1/7] 检查设备...
adb get-state >nul 2>nul
if errorlevel 1 (
  echo [错误] 未检测到手机: 检查数据线 / USB调试开关 / 手机上的授权弹窗
  pause
  exit /b 1
)

echo [2/7] 安装 APK...
adb install -r "%APK%"
if errorlevel 1 (
  echo [错误] 安装失败
  pause
  exit /b 1
)

echo [3/7] 授予定位权限(含始终允许)...
adb shell pm grant %PKG% android.permission.ACCESS_FINE_LOCATION
adb shell pm grant %PKG% android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant %PKG% android.permission.ACCESS_BACKGROUND_LOCATION

echo [4/7] 授予通知权限...
adb shell pm grant %PKG% android.permission.POST_NOTIFICATIONS

echo [5/7] 精确闹钟 + 电池白名单...
adb shell appops set %PKG% SCHEDULE_EXACT_ALARM allow
adb shell dumpsys deviceidle whitelist +%PKG%

echo [6/7] 写入配置并启动服务...
adb shell am start -n %PKG%/.MainActivity --es api_base "%API_BASE%" --es device_key "%DEVICE_KEY%" --es device_name "redmi-note15pro" --ei passive_min 60

echo [7/7] 设置设备所有者(防卸载/强停/开机必自启)...
adb shell dpm set-device-owner --user 0 %PKG%/.DeviceAdmin
if errorlevel 1 (
  echo   [提示] 设置失败不影响定位功能, 代价仅是重启后需手动打开一次 App。
  echo          常见原因: 手机登录着小米账号 → 设置里退出全部账号后重跑本脚本即可。
)

echo.
echo ============================================================
echo   装配完成! 浏览器打开地图验证在线状态:
echo   %API_BASE%/map?token=你的ACCESS_TOKEN
echo ============================================================
pause
