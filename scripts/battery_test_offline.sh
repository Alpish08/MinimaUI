#!/bin/bash
# Battery test with no wireless connection: a logger runs on the RP6 itself while it is unplugged.
#   scripts/battery_test_offline.sh start [interval_seconds]   (default 300) - then unplug the cable
#   scripts/battery_test_offline.sh stop                       - plug the cable back in first
#   scripts/battery_test_offline.sh pull                       - copies the log to ./battery_<build>_<time>.csv and summarises it
# Same prep as battery_test.sh: same charge level and brightness for each build, MinimaUI open on Favorites.
LOG=/sdcard/MinimaUI/battery_log.csv
case "$1" in
  start)
    INT=${2:-300}
    adb shell dumpsys batterystats --reset >/dev/null
    adb shell "echo 'time,level_pct,temp_c,status,current_ma,wakefulness' > $LOG"
    adb shell "nohup setsid sh -c 'while true; do
      L=\$(dumpsys battery | grep -m1 level: | cut -d: -f2 | tr -d \" \");
      T=\$(dumpsys battery | grep -m1 temperature: | cut -d: -f2 | tr -d \" \");
      S=\$(dumpsys battery | grep -m1 status: | cut -d: -f2 | tr -d \" \");
      C=\$(cat /sys/class/power_supply/battery/current_now 2>/dev/null);
      W=\$(dumpsys power | grep -m1 -o \"mWakefulness=[A-Za-z]*\" | cut -d= -f2);
      echo \"\$(date +%H:%M),\$L,\$T,\$S,\$C,\$W\" >> $LOG; sleep $INT; done' >/dev/null 2>&1 &"
    echo "Logger started (every ${INT}s). Unplug the cable now.";;
  stop)
    adb shell "pkill -f 'battery_log' ; for p in \$(ps -A -o PID,ARGS | grep 'battery_log' | grep -v grep | awk '{print \$1}'); do kill \$p; done" >/dev/null 2>&1
    echo "Logger stopped.";;
  pull)
    BUILD=$(adb shell dumpsys package com.minimaui.app | grep -m1 versionName | tr -d ' \r'); OUT="battery_${BUILD#versionName=}_$(date +%m%d_%H%M).csv"
    adb pull $LOG "$OUT" >/dev/null && cat "$OUT" && awk -F, 'NR==2{f=$2} END{print "Level " f "% -> " $2 "%"}' "$OUT";;
  *) echo "usage: $0 start [seconds] | stop | pull";;
esac
