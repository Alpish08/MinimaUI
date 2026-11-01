#!/system/bin/sh
# Runs as root late in boot: turn the overlays on and put them above Iconify's styles (they only replace backgrounds and outlines).
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 8
for OV in com.minimaui.overlay.android com.minimaui.overlay.systemui; do
  cmd overlay enable --user 0 $OV
  cmd overlay set-priority $OV highest
done
