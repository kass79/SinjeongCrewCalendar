@echo off
cd /d %~dp0
call gradlew.bat :app:assembleDebug
echo.
echo ===== DONE =====
echo APK: app\build\outputs\apk\debug\app-debug.apk
pause
