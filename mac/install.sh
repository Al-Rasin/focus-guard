#!/bin/bash
# Installs Focus Guard on macOS. Run once: sudo ./mac/install.sh
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "Run with sudo"; exit 1; }
cd "$(dirname "$0")"

DIR="/Library/Application Support/FocusGuard"
LABEL=com.focusguard.daemon
mkdir -p "$DIR"
rm -rf "$DIR/extension"
cp -R extension "$DIR/extension"
rm -rf "$DIR/keeper"
cp -R keeper "$DIR/keeper"
cp ../android/app/assets/keywords.json "$DIR/extension/"
[ -f ../android/app/assets/keywords.local.json ] && cp ../android/app/assets/keywords.local.json "$DIR/extension/"
cp ../android/magisk/adult_hosts "$DIR/adult_hosts"
cp focusguardd.sh "$DIR/focusguardd.sh"
# Launcher that gets Full Disk Access (needed to read the browsers' Secure Preferences, see runner.c).
# Rebuilding changes its signature, so only rebuild when the source changed (else the grant is lost).
if ! cmp -s runner.c "$DIR/runner.c" || [ ! -x "$DIR/focusguard-runner" ]; then
  # Command Line Tools can lag behind the OS (linker rejects the new libSystem); then try Xcode's SDK.
  cc -O2 -o "$DIR/focusguard-runner" runner.c 2>/dev/null ||
    cc -O2 -isysroot "$(xcrun --sdk macosx --show-sdk-path 2>/dev/null)" -o "$DIR/focusguard-runner" runner.c ||
    cc -O2 -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk \
      -o "$DIR/focusguard-runner" runner.c
  cp runner.c "$DIR/runner.c"
fi
rm -f "$DIR/uninstall-at"
chown -R root:wheel "$DIR"; chmod -R go-w "$DIR"; chmod 755 "$DIR/focusguardd.sh" "$DIR/focusguard-runner"

cat > /Library/LaunchDaemons/$LABEL.plist <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key><array><string>$DIR/focusguard-runner</string></array>
  <key>RunAtLoad</key><true/>
  <key>StartInterval</key><integer>60</integer>
</dict>
</plist>
EOF
chown root:wheel /Library/LaunchDaemons/$LABEL.plist; chmod 644 /Library/LaunchDaemons/$LABEL.plist
launchctl bootout system/$LABEL 2>/dev/null || true
launchctl bootstrap system /Library/LaunchDaemons/$LABEL.plist

echo "Installed. In Chrome and Brave: open the extensions page, turn on Developer mode,"
echo "click 'Load unpacked' and pick: $DIR/extension, then again for: $DIR/keeper"
echo
echo "Then give the daemon Full Disk Access (System Settings > Privacy & Security > Full Disk Access,"
echo "+, Cmd+Shift+G): $DIR/focusguard-runner"
echo "Without it the daemon can't see that the extension is loaded and hard-blocks Shorts and facebook.com."
