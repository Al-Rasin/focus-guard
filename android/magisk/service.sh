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
BAK=/data/adb/focusguard
GUARD=/data/adb/service.d/focusguard.sh
# Apps that refuse to run while our accessibility service is on (bKash). The service turns itself
# off when one comes to the front and writes $EXEMPT_FLAG; it's turned back on once that app is no
# longer focused. Keep in sync with EXEMPT in GuardService.java.
EXEMPT="com.bKash.customerapp"
EXEMPT_FLAG=/data/data/$PKG/files/exempt

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

# A phone call (ringing/off-hook on any SIM) or VoIP call is active. During a call Google
# Play services' scam guard blocks every accessibility change and shows a "Can't complete
# action during call" dialog, so the accessibility steps wait for the next loop after it.
in_call() {
  dumpsys telephony.registry 2>/dev/null | grep -q 'mCallState=[12]' && return 0
  # MIUI's dumpsys audio has no current mMode line; a VoIP call (WhatsApp, ...) shows as a started
  # player with usage=USAGE_VOICE_COMMUNICATION.
  dumpsys audio 2>/dev/null | grep -qE 'mMode=MODE_IN_(CALL|COMMUNICATION)|AudioPlaybackConfiguration .*state:started .*usage=USAGE_VOICE_COMMUNICATION'
}

focused_app() {
  dumpsys window 2>/dev/null | grep -m1 'mFocusedApp=' | grep -oE ' [A-Za-z0-9_.]+/' | tr -d ' /'
}

# An exempt app is in front and the service stepped aside for it: leave accessibility alone.
exempt_active() {
  [ -f "$EXEMPT_FLAG" ] || return 1
  f=$(focused_app)
  for e in $EXEMPT; do [ "$f" = "$e" ] && return 0; done
  return 1
}

# Fast loop (2 s): turn the service back on as soon as the exempt app leaves the front, so it
# can't be used as a 30 s gap. Exits with the watchdog (guardian kills only the watchdog pid).
WD=$$
(
  while kill -0 $WD 2>/dev/null; do
    sleep 2
    if [ -f "$EXEMPT_FLAG" ] && ! exempt_active && ! in_call; then
      rm -f "$EXEMPT_FLAG"
      cur=$(settings get secure enabled_accessibility_services)
      case "$cur" in
        *"$SVC"*) ;;
        null|"") settings put secure enabled_accessibility_services "$SVC" ;;
        *) settings put secure enabled_accessibility_services "$cur:$SVC" ;;
      esac
      settings put secure accessibility_enabled 1
      touch "$BEAT" # don't let 1a treat the pause as a hang
      log "exempt app left, accessibility back on"
    fi
  done
) &

while true; do
  # 00. Guardian (guard.sh) installed and running, and its backup of this module current.
  #     It restores this module if deleted; this restores it if deleted.
  mkdir -p "$BAK/module"
  for f in "$MODDIR"/*; do
    case "${f##*/}" in watchdog.*|limits.bak|system|update) continue ;; esac
    cmp -s "$f" "$BAK/module/${f##*/}" || cp "$f" "$BAK/module/"
  done
  cmp -s "$MODDIR/remove.sh" "$BAK/remove.sh" || cp "$MODDIR/remove.sh" "$BAK/remove.sh"
  if ! cmp -s "$MODDIR/guard.sh" "$GUARD"; then
    mkdir -p "${GUARD%/*}" && cp "$MODDIR/guard.sh" "$GUARD" && chmod 755 "$GUARD"
    log "installed guardian"
  fi
  gp=$(cat "$BAK/guard.pid" 2>/dev/null)
  if [ -z "$gp" ] || ! grep -q focusguard /proc/$gp/cmdline 2>/dev/null; then
    sh "$GUARD"
    log "started guardian"
  fi

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

  # 1. Accessibility service on (keep any other enabled services). Skipped during a call
  #    (see in_call) up to the end of 1b.
  if exempt_active; then
    : # service is off on purpose while bKash etc. is in front (fast loop above turns it back on)
  elif in_call; then
    [ -z "$call_logged" ] && log "call active, accessibility checks paused" && call_logged=1
  else
    [ -n "$call_logged" ] && log "call ended, accessibility checks resumed" && call_logged=
    cur=$(settings get secure enabled_accessibility_services)
    case "$cur" in
      *"$SVC"*) ;;
      null|"") settings put secure enabled_accessibility_services "$SVC" ;;
      *) settings put secure enabled_accessibility_services "$cur:$SVC" ;;
    esac
    [ "$(settings get secure accessibility_enabled)" = 1 ] || settings put secure accessibility_enabled 1

    # Tell the app a watchdog is here: it then skips the non-root setup steps and alerts.
    [ -d "${BEAT%/*}" ] && touch "${BEAT%/*}/rooted"

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
      rest=$(echo "$cur" | tr ':' '\n' | grep -v -e "$PKG" -e "^null$" | paste -sd: -)
      if [ -n "$rest" ]; then settings put secure enabled_accessibility_services "$rest"
      else settings delete secure enabled_accessibility_services; fi
      sleep 1
      settings put secure enabled_accessibility_services "${rest:+$rest:}$SVC"
      log "rebound accessibility service"
    fi
  fi

  # 2. Never killed by battery saver / MIUI.
  dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1
  cmd appops set $PKG RUN_IN_BACKGROUND allow 2>/dev/null
  cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow 2>/dev/null
  # MIUI must allow the blocking activity to open from the accessibility service.
  if [ -n "$(getprop ro.miui.ui.version.name)" ]; then
    cmd appops set $PKG 10021 allow 2>/dev/null
  fi

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
