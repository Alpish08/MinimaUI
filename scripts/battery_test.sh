#!/bin/bash
# Battery test over wireless debugging, so the cable can stay out.
#   scripts/battery_test.sh <ip:port> [hours]      e.g. scripts/battery_test.sh 192.168.1.50:41234 3
# Before: pair once (Developer options > Wireless debugging > Pair device with code, then `adb pair <ip:pairport>`),
# charge to the same level for each build you compare, set the same brightness, open MinimaUI on Favorites, then run this
# and unplug the cable. It logs every 5 minutes to battery_<build>.csv and prints the drop per hour at the end.
DEV=${1:?usage: battery_test.sh <ip:port> [hours]}
HOURS=${2:-3}
adb connect "$DEV" >/dev/null
d() { adb -s "$DEV" "$@"; }
BUILD=$(d shell dumpsys package com.minimaui.app | grep -m1 versionName | tr -d ' \r')
OUT="battery_${BUILD#versionName=}_$(date +%m%d_%H%M).csv"
echo "time,level_pct,temp_c,status,current_ma,screen_on" > "$OUT"
d shell dumpsys batterystats --reset >/dev/null
echo "Unplug the USB cable now. Logging to $OUT for $HOURS h."
START=$(d shell dumpsys battery | awk '/level/ {print $2}' | tr -d '\r')
END=$(( $(date +%s) + HOURS * 3600 ))
while [ "$(date +%s)" -lt "$END" ]; do
  b=$(d shell dumpsys battery)
  lvl=$(echo "$b" | awk '/level:/ {print $2}' | tr -d '\r'); tmp=$(echo "$b" | awk '/temperature:/ {print $2/10}' | tr -d '\r')
  st=$(echo "$b" | awk '/status:/ {print $2}' | tr -d '\r')
  cur=$(d shell cat /sys/class/power_supply/battery/current_now 2>/dev/null | tr -d '\r' | awk '{printf "%d", $1/1000}')
  scr=$(d shell dumpsys power | grep -m1 -o "mWakefulness=[A-Za-z]*" | cut -d= -f2)
  echo "$(date +%H:%M),$lvl,$tmp,$st,$cur,$scr" | tee -a "$OUT"
  sleep 300
done
LAST=$(tail -1 "$OUT" | cut -d, -f2)
echo "Level $START% -> $LAST% in $HOURS h ($(( (START - LAST) * 10 / HOURS ))/10 % per hour)"
d shell dumpsys batterystats com.minimaui.app | grep -iE "Estimated power use|Uid u0a|cpu|screen" | head -15
