#!/bin/bash
# Builds build/MinimaUIVolumeHook.apk, an LSPosed module (legacy Xposed API) for com.android.systemui. The Xposed API classes are only
# stubs for the compiler (stubs/): LSPosed supplies the real ones at run time, so they are not packaged.
set -e
SDK=~/Library/Android/sdk; BT=$SDK/build-tools/36.0.0; AJ=$SDK/platforms/android-36/android.jar
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/stubs build/classes build/dex build/assets
$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -o build/base.apk -I $AJ --manifest AndroidManifest.xml --min-sdk-version 33 --target-sdk-version 34 build/res.zip
"$JAVA_HOME/bin/javac" --release 8 -cp $AJ -d build/stubs $(find stubs -name '*.java') 2>&1 | grep -v "^Note\|warning" || true
"$JAVA_HOME/bin/javac" --release 8 -cp $AJ:build/stubs -d build/classes $(find src -name '*.java') 2>&1 | grep -v "^Note\|warning" || true
$BT/d8 --lib $AJ --min-api 33 --output build/dex $(find build/classes -name '*.class')
echo "com.minimaui.uihook.Hook" > build/assets/xposed_init
cp build/base.apk build/app.apk
(cd build/dex && zip -q -j ../app.apk classes.dex)
(cd build && zip -q -r app.apk assets)
$BT/zipalign -f -p 4 build/app.apk build/aligned.apk
$BT/apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --out build/MinimaUIVolumeHook.apk build/aligned.apk 2>&1 | grep -v "^WARNING" || true
echo "built build/MinimaUIVolumeHook.apk"
