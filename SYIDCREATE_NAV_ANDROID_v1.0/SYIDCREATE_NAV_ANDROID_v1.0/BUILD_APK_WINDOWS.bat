@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle belum tersedia. Buka proyek ini memakai Android Studio dan pilih Build ^> Build APK(s).
  pause
  exit /b 1
)
gradle assembleDebug
if errorlevel 1 (
  echo Build gagal. Periksa pesan di atas.
  pause
  exit /b 1
)
echo.
echo APK selesai:
echo app\build\outputs\apk\debug\app-debug.apk
pause
