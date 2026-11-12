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
# Assets: app/assets plus the adult domain list (URL check on phones without root).
cp -R app/assets $OUT/assets && cp magisk/adult_hosts $OUT/assets/
$BT/aapt2 link -o $OUT/base.apk -I $JAR --manifest app/AndroidManifest.xml \
    -A $OUT/assets $OUT/res.zip

javac --release 11 -nowarn -cp $JAR -d $OUT/classes $(find app/src -name '*.java')
$BT/d8 --min-api 26 --lib $JAR --output $OUT $(find $OUT/classes -name '*.class')
(cd $OUT && zip -q base.apk classes.dex)

$BT/zipalign -f 4 $OUT/base.apk $OUT/aligned.apk
# Signing: release builds set FG_KEYSTORE + FG_KS_PASS (env). Otherwise a local key is made once.
# Keep using the same key: Android refuses an update signed with a different one.
KS=${FG_KEYSTORE:-focusguard.keystore}
export KSPASS=${FG_KS_PASS:-focusguard}
if [ ! -f "$KS" ]; then
    [ -n "${FG_KEYSTORE:-}" ] && { echo "Keystore $KS not found" >&2; exit 1; }
    keytool -genkeypair -keystore "$KS" -alias fg -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$KSPASS" -keypass "$KSPASS" -dname "CN=Focus Guard" >/dev/null 2>&1
fi
$BT/apksigner sign --ks "$KS" --ks-pass env:KSPASS --out FocusGuard.apk $OUT/aligned.apk
echo "Built $(pwd)/FocusGuard.apk"

# Flashable Magisk module: watchdog + bundled APK, versions taken from the manifest.
CODE=$(sed -n 's/.*android:versionCode="\([0-9]*\)".*/\1/p' app/AndroidManifest.xml)
NAME=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' app/AndroidManifest.xml)
MOD=$OUT/module
mkdir -p $MOD/META-INF/com/google/android
cp magisk/{customize.sh,service.sh,action.sh,uninstall.sh,guard.sh,remove.sh,adult_hosts,private_dns} FocusGuard.apk $MOD/
sed "s/^version=.*/version=$NAME/; s/^versionCode=.*/versionCode=$CODE/" magisk/module.prop > $MOD/module.prop
echo "$CODE" > $MOD/apk_version
# Standard Magisk installer stub.
printf '#!/sbin/sh\n#################\n# Initialization\n#################\numask 022\nui_print() { echo "$1"; }\nOUTFD=$2\nZIPFILE=$3\n. /data/adb/magisk/util_functions.sh\ninstall_module\nexit 0\n' \
    > $MOD/META-INF/com/google/android/update-binary
echo "#MAGISK" > $MOD/META-INF/com/google/android/updater-script
rm -f FocusGuard-module.zip && (cd $MOD && zip -qr ../../FocusGuard-module.zip .)
echo "Built $(pwd)/FocusGuard-module.zip"
