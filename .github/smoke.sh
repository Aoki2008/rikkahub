#!/usr/bin/env bash
# Smoke test: install the debug APK on the running emulator, launch the app,
# and fail if it crashes on startup. Run as a single line from the
# android-emulator-runner `script:` (which executes each script line in its own
# shell — so all logic must live here in one file).
set -e

APK=$(find apk -name '*x86_64*.apk' | head -1)
[ -z "$APK" ] && APK=$(find apk -name '*universal*.apk' | head -1)
[ -z "$APK" ] && APK=$(find apk -name '*.apk' | head -1)
echo "Selected APK: $APK"
if [ -z "$APK" ]; then
  echo "No APK found in artifact:"; ls -R apk || true; exit 1
fi

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n me.rerere.rikkahub.st.debug/me.rerere.rikkahub.RouteActivity
sleep 25

adb logcat -d -b crash > crash.txt 2>/dev/null || true
if [ -s crash.txt ]; then
  echo "=== CRASH DETECTED ==="
  cat crash.txt
  exit 1
fi

if adb shell pidof me.rerere.rikkahub.st.debug >/dev/null 2>&1; then
  echo "PASS: app launched and is still running (no crash on startup)"
else
  echo "FAIL: app process not running after launch"
  adb logcat -d | tail -200
  exit 1
fi
