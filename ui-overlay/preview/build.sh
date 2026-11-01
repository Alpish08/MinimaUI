#!/bin/bash
# Builds build/QSPreview.apk: draws the overlay's drawables on the device, so the look can be judged without flashing the module.
set -e
SDK=~/Library/Android/sdk; BT=$SDK/build-tools/36.0.0; AJ=$SDK/platforms/android-36/android.jar
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd "$(dirname "$0")"
[ -n "$SKIPGEN" ] || python3 ../generate.py >/dev/null
rm -rf build && mkdir -p build/gen build/classes build/dex
$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -o build/base.apk -I $AJ --manifest AndroidManifest.xml --java build/gen --min-sdk-version 33 --target-sdk-version 34 build/res.zip
"$JAVA_HOME/bin/javac" --release 8 -cp $AJ -d build/classes build/gen/com/minimaui/qspreview/R.java src/com/minimaui/qspreview/MainActivity.java 2>&1 | grep -v "^Note\|warning" || true
$BT/d8 --lib $AJ --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/app.apk
(cd build/dex && zip -q -j ../app.apk classes.dex)
$BT/zipalign -f -p 4 build/app.apk build/aligned.apk
$BT/apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --out build/QSPreview.apk build/aligned.apk 2>&1 | grep -v "^WARNING" || true
echo "built build/QSPreview.apk"
