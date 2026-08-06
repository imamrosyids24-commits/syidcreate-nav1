#!/usr/bin/env sh
set -eu
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle belum tersedia. Buka proyek ini memakai Android Studio lalu pilih Build > Build APK(s)."
  exit 1
fi
gradle assembleDebug
echo "APK selesai: app/build/outputs/apk/debug/app-debug.apk"
