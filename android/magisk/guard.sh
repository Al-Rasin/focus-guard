#!/system/bin/sh
# Focus Guard guardian. Lives outside the module as /data/adb/service.d/focusguard.sh, so it
# survives removing the module in Magisk. Every 10 s it:
#   - deletes the `remove` / `disable` flags Magisk's module toggles create,
#   - puts the module back from the backup in /data/adb/focusguard if it was deleted,
#   - starts the watchdog (service.sh) if it isn't running.
# The watchdog in turn puts this file back if it's deleted.
# The only way out is the delayed removal: `su -c sh /data/adb/focusguard/remove.sh` (24 h).
BAK=/data/adb/focusguard
MOD=/data/adb/modules/focusguard
UPD=/data/adb/modules_update/focusguard
SELF=/data/adb/service.d/focusguard.sh
PIDFILE=$BAK/guard.pid

[ "$1" = loop ] || { setsid nohup sh "$0" loop </dev/null >/dev/null 2>&1 & exit 0; }

old=$(cat "$PIDFILE" 2>/dev/null)
[ -n "$old" ] && [ "$old" != $$ ] && grep -q focusguard /proc/$old/cmdline 2>/dev/null && kill "$old"
mkdir -p "$BAK" && echo $$ > "$PIDFILE"

log() { echo "$(date '+%m-%d %H:%M:%S') guard: $*" >> "$BAK/guard.log"; }

watchdog_running() {
  for d in "$UPD" "$MOD"; do
    p=$(cat "$d/watchdog.pid" 2>/dev/null)
    [ -n "$p" ] && grep -q service.sh /proc/$p/cmdline 2>/dev/null && return 0
  done
  return 1
}

# Delayed removal is due: undo everything and delete every copy, this file last.
remove_all() {
  log "scheduled removal: removing Focus Guard"
  for d in "$UPD" "$MOD"; do
    p=$(cat "$d/watchdog.pid" 2>/dev/null)
    [ -n "$p" ] && grep -q service.sh /proc/$p/cmdline 2>/dev/null && kill "$p"
  done
  PKG=com.alrasin.focusguard
  HOSTS=/data/adb/modules/hosts/system/etc/hosts
  [ -f "$HOSTS" ] && sed -i '/# focusguard-adult-start/,/# focusguard-adult-end/d' "$HOSTS"
  rest=$(settings get secure enabled_accessibility_services | tr ':' '\n' | grep -v -e "$PKG" -e "^null$" | paste -sd: -)
  if [ -n "$rest" ]; then settings put secure enabled_accessibility_services "$rest"
  else settings delete secure enabled_accessibility_services; fi
  settings put global private_dns_mode opportunistic
  settings delete global private_dns_specifier
  dumpsys deviceidle whitelist -$PKG >/dev/null 2>&1
  pm uninstall $PKG >/dev/null 2>&1
  rm -rf "$UPD" "$MOD" "$BAK" "$SELF"
  exit 0
}

until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done

while true; do
  at=$(cat "$BAK/remove_at" 2>/dev/null)
  [ -n "$at" ] && [ "$(date +%s)" -ge "$at" ] && remove_all

  for d in "$UPD" "$MOD"; do
    for f in remove disable; do
      [ -e "$d/$f" ] && rm -f "$d/$f" && log "undid module $f flag"
    done
  done

  if [ ! -f "$MOD/service.sh" ] && [ ! -f "$UPD/service.sh" ] && [ -f "$BAK/module/service.sh" ]; then
    mkdir -p "$MOD" && cp -a "$BAK/module/." "$MOD/" && chmod 755 "$MOD"/*.sh
    log "module was deleted, restored"
  fi

  if ! watchdog_running; then
    d=$MOD; [ -f "$UPD/service.sh" ] && d=$UPD
    [ -f "$d/service.sh" ] && setsid nohup sh "$d/service.sh" </dev/null >/dev/null 2>&1 &
    log "started watchdog from $d"
  fi

  sleep 10
done
