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
rm -f "$DIR/uninstall-at"
chown -R root:wheel "$DIR"; chmod -R go-w "$DIR"; chmod 755 "$DIR/focusguardd.sh"

cat > /Library/LaunchDaemons/$LABEL.plist <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key><array><string>/bin/bash</string><string>$DIR/focusguardd.sh</string></array>
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
