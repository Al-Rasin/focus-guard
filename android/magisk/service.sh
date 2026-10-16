#!/system/bin/sh
# Focus Guard watchdog: every 30 s make sure blocking is still in place.
MODDIR=${0%/*}
SVC=com.alrasin.focusguard/com.alrasin.focusguard.GuardService
PKG=com.alrasin.focusguard
MARK="# focusguard-adult-start"
DNS=$(cat "$MODDIR/private_dns" 2>/dev/null)
APK_VERSION=$(cat "$MODDIR/apk_version" 2>/dev/null)
LOG=$MODDIR/watchdog.log
PIDFILE=$MODDIR/watchdog.pid
PREFS=/data/data/$PKG/shared_prefs/limits.xml
BEAT=/data/data/$PKG/files/heartbeat

log() {
  echo "$(date '+%m-%d %H:%M:%S') $*" >> "$LOG"
  [ "$(wc -l < "$LOG")" -gt 300 ] && tail -n 150 "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"
}

# One watchdog only: a manual start replaces the one Magisk started at boot.
old=$(cat "$PIDFILE" 2>/dev/null)
[ -n "$old" ] && [ "$old" != $$ ] && grep -q service.sh /proc/$old/cmdline 2>/dev/null && kill "$old" && pkill -P "$old"
echo $$ > "$PIDFILE"

until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done
sleep 20
log "started (pid $$)"

# AdAway's systemless hosts module if present, else the one customize.sh added to this module.
# /system/etc/hosts is a read-only bind of that file, so edit the source.
HOSTS=/data/adb/modules/hosts/system/etc/hosts
[ -f "$HOSTS" ] || HOSTS=$MODDIR/system/etc/hosts

installed_version() {
  dumpsys package $PKG 2>/dev/null | grep -m1 -o 'versionCode=[0-9]*' | cut -d= -f2
}

while true; do
  # 0. App present and at least the bundled version (reinstalls after an uninstall).
  if [ -f "$MODDIR/FocusGuard.apk" ] && [ -n "$APK_VERSION" ]; then
    v=$(installed_version)
    if [ -z "$v" ] || [ "$v" -lt "$APK_VERSION" ]; then
      if pm install -r -S "$(stat -c %s "$MODDIR/FocusGuard.apk")" < "$MODDIR/FocusGuard.apk" >/dev/null 2>&1; then
        log "installed app v$APK_VERSION (was ${v:-missing})"
      fi
    fi
  fi

  # 0b. Today's open counts survive an uninstall or `pm clear` (both wipe app data and
  #     force-stop the app, so restoring before the rebind below is safe).
  if [ -f "$PREFS" ]; then
    cp "$PREFS" "$MODDIR/limits.bak"
  elif [ -f "$MODDIR/limits.bak" ] && uid=$(stat -c %u "/data/data/$PKG" 2>/dev/null); then
    mkdir -p "${PREFS%/*}" && cp "$MODDIR/limits.bak" "$PREFS"
    chown -R "$uid:$uid" "${PREFS%/*}" && chmod 660 "$PREFS" && restorecon -R "${PREFS%/*}"
    log "restored open counts"
  fi

  # 1. Accessibility service on (keep any other enabled services).
  cur=$(settings get secure enabled_accessibility_services)
  case "$cur" in
    *"$SVC"*) ;;
    null|"") settings put secure enabled_accessibility_services "$SVC" ;;
    *) settings put secure enabled_accessibility_services "$cur:$SVC" ;;
  esac
  settings put secure accessibility_enabled 1

  # 1a. Bound but wedged (seen on MIUI: a Short played with the pause long expired, fixed by a
  #     restart). The service touches files/heartbeat every 5 s; stale for 60 s with the
  #     screen on means it stopped working. Force-stop it; 1b below binds it again.
  if [ -f "$BEAT" ] && dumpsys power | grep -q "mWakefulness=Awake" \
     && [ $(( $(date +%s) - $(stat -c %Y "$BEAT") )) -gt 60 ]; then
    am force-stop $PKG
    touch "$BEAT" # give the restarted service time to beat again
    sleep 1
    log "service stopped responding, restarted"
  fi

  # 1b. Enabled is not enough: after a force-stop Android keeps it listed but unbound.
  #     Toggle it off and on to make the system bind it again.
  if ! dumpsys accessibility | grep "Bound services" | grep -q "label=Focus Guard"; then
    cur=$(settings get secure enabled_accessibility_services)
    rest=$(echo "$cur" | tr ':' '\n' | grep -v "$PKG" | paste -sd: -)
    if [ -n "$rest" ]; then settings put secure enabled_accessibility_services "$rest"
    else settings delete secure enabled_accessibility_services; fi
    sleep 1
    settings put secure enabled_accessibility_services "${rest:+$rest:}$SVC"
    log "rebound accessibility service"
  fi

  # 2. Never killed by battery saver / MIUI.
  dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1
  cmd appops set $PKG RUN_IN_BACKGROUND allow 2>/dev/null
  cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow 2>/dev/null

  # 3. Adult hosts list present (AdAway rewrites the file on update).
  if [ -f "$MODDIR/adult_hosts" ] && [ -f "$HOSTS" ] && ! grep -q "$MARK" "$HOSTS"; then
    { echo "$MARK"; cat "$MODDIR/adult_hosts"; echo "# focusguard-adult-end"; } >> "$HOSTS"
    log "re-added adult hosts to $HOSTS"
  fi

  # 4. Family-filter Private DNS (also covers browsers' secure DNS and Telegram's browser).
  if [ -n "$DNS" ]; then
    [ "$(settings get global private_dns_mode)" = hostname ] || settings put global private_dns_mode hostname
    [ "$(settings get global private_dns_specifier)" = "$DNS" ] || settings put global private_dns_specifier "$DNS"
  fi

  sleep 30
done
