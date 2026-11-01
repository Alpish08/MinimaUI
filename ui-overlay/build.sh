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
(cd build/stage && zip -q -r ../MinimaUI-Outlines-Magisk.zip . -x '.DS_Store')
echo "built build/MinimaUI-Outlines-Magisk.zip"
