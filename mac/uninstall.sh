#!/bin/bash
# Requests removal of Focus Guard. Nothing happens now: the daemon removes everything 24 h later,
# so a weak moment passes first. `sudo ./mac/uninstall.sh cancel` withdraws the request.
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "Run with sudo"; exit 1; }
F="/Library/Application Support/FocusGuard/uninstall-at"
if [ "${1:-}" = cancel ]; then rm -f "$F"; echo "Uninstall cancelled."; exit 0; fi
[ -f "$F" ] || echo $(( $(date +%s) + 86400 )) > "$F"
echo "Focus Guard will be removed at $(date -r "$(cat "$F")")."
