#!/bin/bash
# Builds build/MinimaUI-Outlines-Magisk.zip: the SystemUI and framework overlay APKs inside a Magisk module.
set -e
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
SDK=~/Library/Android/sdk; BT=$SDK/build-tools/36.0.0; AJ=$SDK/platforms/android-36/android.jar
cd "$(dirname "$0")"
python3 generate.py >/dev/null
# The overlays link against the device's own framework (Android 13), so private names like Theme.DeviceDefault.SystemUI and
# textColorOnAccent get this ROM's numeric ids. Cached in .cache/ (needs the RP6 connected the first time).
mkdir -p .cache
[ -s .cache/framework-res.apk ] || adb pull /system/framework/framework-res.apk .cache/framework-res.apk >/dev/null
AJ=.cache/framework-res.apk
rm -rf build && mkdir -p build/stage
build_overlay() {  # <source dir> <apk name>
  $BT/aapt2 compile --dir "$1/res" -o "build/$1.zip"
  $BT/aapt2 link -o "build/$1-unsigned.apk" -I $AJ --manifest "$1/AndroidManifest.xml" --min-sdk-version 33 --target-sdk-version 33 \
    --version-code 2 --version-name 2.0 "build/$1.zip"
  $BT/zipalign -f -p 4 "build/$1-unsigned.apk" "build/$1-aligned.apk"
  $BT/apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --out "build/$2" "build/$1-aligned.apk" 2>&1 | grep -v "^WARNING" || true
}
build_overlay systemui MinimaUIOutlinesSystemUI.apk
build_overlay android MinimaUIOutlinesAndroid.apk
cp -R module/. build/stage/
mkdir -p build/stage/system/product/overlay
cp build/MinimaUIOutlinesSystemUI.apk build/MinimaUIOutlinesAndroid.apk build/stage/system/product/overlay/
# Optional: your own screenshot sound replaces Android's shutter click (the screenshot sound is the camera click). It is taken from
# MinimaUI/Sounds/screenshot.(wav|ogg|mp3|m4a) on the device, or from the file named in SCREENSHOT_SOUND, and only ends up in the zip
# under build/ (never in the repository: the sound is yours).
SND="${SCREENSHOT_SOUND:-}"
if [ -z "$SND" ]; then
  for ext in wav ogg mp3 m4a; do
    if adb shell "[ -f /sdcard/MinimaUI/Sounds/screenshot.$ext ]" 2>/dev/null; then
      adb pull "/sdcard/MinimaUI/Sounds/screenshot.$ext" ".cache/screenshot.$ext" >/dev/null && SND=".cache/screenshot.$ext" && break
    fi
  done
fi
if [ -n "$SND" ]; then
  # Vorbis in Ogg, 44.1 kHz stereo, which every Android version plays; the shutter sound is short.
  ffmpeg -y -loglevel error -i "$SND" -ar 44100 -ac 2 -c:a vorbis -strict -2 -b:a 160k .cache/camera_click.ogg
  # MediaActionSound looks in /product/media/audio/ui first, then /system/media/audio/ui: replace the file wherever the device has it
  # (both when the RP6 isn't connected).
  PLACES=""
  for dir in system product system_ext; do
    if adb shell "[ -f /$dir/media/audio/ui/camera_click.ogg ]" 2>/dev/null; then PLACES="$PLACES $dir"; fi
  done
  [ -n "$PLACES" ] || PLACES="system product"
  for dir in $PLACES; do
    if [ "$dir" = system ]; then target=build/stage/system/media/audio/ui; else target=build/stage/system/$dir/media/audio/ui; fi
    mkdir -p "$target" && cp .cache/camera_click.ogg "$target/camera_click.ogg"
  done
  echo "screenshot sound: $SND replaces the camera click in:$PLACES"
else
  echo "no screenshot sound found (put screenshot.wav in MinimaUI/Sounds/ on the device, or set SCREENSHOT_SOUND)"
fi
(cd build/stage && zip -q -r ../MinimaUI-Outlines-Magisk.zip . -x '.DS_Store')
echo "built build/MinimaUI-Outlines-Magisk.zip"
