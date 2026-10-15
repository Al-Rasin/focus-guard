#!/bin/bash
# Builds Focus Guard APK without Gradle: javac -> d8 -> aapt2 -> zipalign -> apksigner.
set -euo pipefail
cd "$(dirname "$0")"

SDK=~/Library/Android/sdk
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-34/android.jar
OUT=build
rm -rf $OUT && mkdir -p $OUT/classes $OUT/res

$BT/aapt2 compile --dir app/res -o $OUT/res.zip
$BT/aapt2 link -o $OUT/base.apk -I $JAR --manifest app/AndroidManifest.xml \
    -A app/assets $OUT/res.zip

javac --release 11 -nowarn -cp $JAR -d $OUT/classes $(find app/src -name '*.java')
$BT/d8 --min-api 26 --lib $JAR --output $OUT $(find $OUT/classes -name '*.class')
(cd $OUT && zip -q base.apk classes.dex)

$BT/zipalign -f 4 $OUT/base.apk $OUT/aligned.apk
[ -f focusguard.keystore ] || keytool -genkeypair -keystore focusguard.keystore -alias fg \
    -keyalg RSA -keysize 2048 -validity 10000 -storepass focusguard -keypass focusguard \
    -dname "CN=Focus Guard" >/dev/null 2>&1
$BT/apksigner sign --ks focusguard.keystore --ks-pass pass:focusguard --out FocusGuard.apk $OUT/aligned.apk
echo "Built $(pwd)/FocusGuard.apk"

# Flashable Magisk module: watchdog + bundled APK, versions taken from the manifest.
CODE=$(sed -n 's/.*android:versionCode="\([0-9]*\)".*/\1/p' app/AndroidManifest.xml)
NAME=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' app/AndroidManifest.xml)
MOD=$OUT/module
mkdir -p $MOD/META-INF/com/google/android
cp magisk/{customize.sh,service.sh,action.sh,uninstall.sh,adult_hosts,private_dns} FocusGuard.apk $MOD/
sed "s/^version=.*/version=$NAME/; s/^versionCode=.*/versionCode=$CODE/" magisk/module.prop > $MOD/module.prop
echo "$CODE" > $MOD/apk_version
# Standard Magisk installer stub.
printf '#!/sbin/sh\n#################\n# Initialization\n#################\numask 022\nui_print() { echo "$1"; }\nOUTFD=$2\nZIPFILE=$3\n. /data/adb/magisk/util_functions.sh\ninstall_module\nexit 0\n' \
    > $MOD/META-INF/com/google/android/update-binary
echo "#MAGISK" > $MOD/META-INF/com/google/android/updater-script
rm -f FocusGuard-module.zip && (cd $MOD && zip -qr ../../FocusGuard-module.zip .)
echo "Built $(pwd)/FocusGuard-module.zip"
