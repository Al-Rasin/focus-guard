#!/system/bin/sh
# Runs on the boot after the module is removed in Magisk: undo everything the watchdog set,
# so the phone isn't left with a strict Private DNS or a dangling accessibility entry.
# Magisk calls this early in boot, before `settings`/`pm` work, so the cleanup runs detached
# (the subshell is parsed whole before Magisk deletes this file).
PKG=com.alrasin.focusguard

(
  until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done
  sleep 10

  HOSTS=/data/adb/modules/hosts/system/etc/hosts
  [ -f "$HOSTS" ] && sed -i '/# focusguard-adult-start/,/# focusguard-adult-end/d' "$HOSTS"

  rest=$(settings get secure enabled_accessibility_services | tr ':' '\n' | grep -v "$PKG" | paste -sd: -)
  if [ -n "$rest" ] && [ "$rest" != null ]; then settings put secure enabled_accessibility_services "$rest"
  else settings delete secure enabled_accessibility_services; fi

  settings put global private_dns_mode opportunistic
  settings delete global private_dns_specifier
  dumpsys deviceidle whitelist -$PKG >/dev/null 2>&1
  pm uninstall $PKG >/dev/null 2>&1
) </dev/null >/dev/null 2>&1 &
