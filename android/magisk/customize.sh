#!/system/bin/sh
# Runs once when the zip is flashed in Magisk. Installs the bundled app; the watchdog
# (service.sh) does the rest on every boot.
SKIPUNZIP=0
PKG=com.alrasin.focusguard

ui_print "- Focus Guard $(grep_prop version "$MODPATH/module.prop")"

# Without AdAway/systemless hosts, ship our own hosts file (original + adult list).
if [ ! -f /data/adb/modules/hosts/system/etc/hosts ]; then
  ui_print "- No systemless hosts module: adding one"
  mkdir -p "$MODPATH/system/etc"
  cat /system/etc/hosts > "$MODPATH/system/etc/hosts"
  set_perm "$MODPATH/system/etc/hosts" 0 0 0644
fi

if $BOOTMODE; then
  ui_print "- Installing app"
  # system_server can't read /data/adb, so stream the APK through stdin.
  if pm install -r -S "$(stat -c %s "$MODPATH/FocusGuard.apk")" < "$MODPATH/FocusGuard.apk" >/dev/null 2>&1; then
    ui_print "  done"
  else
    ui_print "  failed; the watchdog retries after reboot"
  fi
fi

set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
ui_print "- Reboot to start. Status: Magisk > Modules > Focus Guard > Action"
