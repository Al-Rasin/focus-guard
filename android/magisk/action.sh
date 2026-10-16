#!/system/bin/sh
# Magisk "Action" button: show whether every layer of Focus Guard is working.
MODDIR=${0%/*}
PKG=com.alrasin.focusguard
HOSTS=/data/adb/modules/hosts/system/etc/hosts
[ -f "$HOSTS" ] || HOSTS=$MODDIR/system/etc/hosts

check() { if eval "$2" >/dev/null 2>&1; then echo "  OK    $1"; else echo "  FAIL  $1"; fi; }

echo "Focus Guard status"
pid=$(cat "$MODDIR/watchdog.pid" 2>/dev/null)
check "Watchdog running" "[ -n '$pid' ] && grep -q service.sh /proc/$pid/cmdline"
check "App installed" "pm path $PKG"
check "Accessibility bound" "dumpsys accessibility | grep 'Bound services' | grep -q 'label=Focus Guard'"
beat=/data/data/$PKG/files/heartbeat
check "Service responding" "[ \$(( \$(date +%s) - \$(stat -c %Y $beat) )) -le 15 ]"
check "Adult hosts block" "grep -q '# focusguard-adult-start' '$HOSTS'"
check "Private DNS filter" "[ \"\$(settings get global private_dns_specifier)\" = \"\$(cat $MODDIR/private_dns)\" ]"

prefs=/data/data/$PKG/shared_prefs/limits.xml
day=$(date +%Y%m%d)
used() {
  n=$(sed -n "s/.*name=\"${day}_$1\" value=\"\([0-9]*\)\".*/\1/p" "$prefs" 2>/dev/null)
  echo "${n:-0}"
}
echo
echo "Opens used today: Shorts $(used shorts), Reels $(used reels)"
echo
echo "Recent watchdog log:"
tail -n 8 "$MODDIR/watchdog.log" 2>/dev/null | sed 's/^/  /'
