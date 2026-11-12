#!/system/bin/sh
# Schedule Focus Guard's removal 24 h from now (the guardian does it when due).
#   su -c sh /data/adb/focusguard/remove.sh          schedule
#   su -c sh /data/adb/focusguard/remove.sh cancel   withdraw
AT=/data/adb/focusguard/remove_at
if [ "$1" = cancel ]; then
  rm -f "$AT" && echo "Removal cancelled."
elif [ -f "$AT" ]; then
  echo "Removal already scheduled for $(date -d @"$(cat "$AT")" '+%Y-%m-%d %H:%M')."
else
  echo $(( $(date +%s) + 86400 )) > "$AT"
  echo "Focus Guard will be removed at $(date -d @"$(cat "$AT")" '+%Y-%m-%d %H:%M'). Cancel: sh $0 cancel"
fi
