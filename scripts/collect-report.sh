#!/system/bin/sh
# HyperFCM report collector. Needs root:
#   su -c 'sh /sdcard/Download/collect-report.sh'
# It only reads system state and logs; it changes nothing.

OUT="/sdcard/Download/${1:-hyperfcm-report.txt}"
GMS=com.google.android.gms
MODULE=io.github.kenanjkm.hyperfcm

section() { echo; echo "== $1"; }

{
  echo "HyperFCM report $(date '+%Y-%m-%d %H:%M:%S %z')"

  section "System"
  echo "hyperos=$(getprop ro.mi.os.version.incremental)"
  echo "android=$(getprop ro.build.version.release) sdk=$(getprop ro.build.version.sdk)"
  echo "model=$(getprop ro.product.model) region=$(getprop ro.miui.region)"

  section "Module"
  dumpsys package "$MODULE" | grep -m1 versionName || echo "$MODULE is not installed"

  section "Module log"
  logcat -d -s HyperFCM | tail -n 300

  section "Greeze (freezer)"
  dumpsys greezer | grep -E "mCurrentCNPolicy|mGmsLimitEnabled|$GMS" | head -n 40

  section "FCM connection"
  dumpsys activity service "$GMS/.gcm.GcmService" | grep -iE "connected|Heartbeat" | head -n 10

  section "Doze"
  dumpsys deviceidle | grep -E "mState=|mLightState=" | head -n 4
  dumpsys deviceidle whitelist | grep -i gms

  section "PowerKeeper sleep mode"
  logcat -d | grep -iE "phoneSleepMode|PowerKeeper.GMS" | tail -n 40
} > "$OUT" 2>&1

echo "Saved to $OUT"
