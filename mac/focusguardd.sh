#!/bin/bash
# Focus Guard Mac daemon. Runs as root every 60 s (LaunchDaemon) and puts everything back:
# browser policies, extension lock, adult hosts list. Also handles the 24 h delayed uninstall.
DIR="/Library/Application Support/FocusGuard"
EXT_ID="jbdijfcfljfehjhfjjlfkhkfpgjihndk"
EXT_DIR="$DIR/extension"
KEEPER_ID="llekdneeeimgoojkcjlfbajlkkcklnal"
KEEPER_DIR="$DIR/keeper"
MP="/Library/Managed Preferences"
HOSTS=/etc/hosts
LABEL=com.focusguard.daemon

# ---- delayed uninstall (requested by uninstall.sh) ----
if [ -f "$DIR/uninstall-at" ] && [ "$(date +%s)" -ge "$(cat "$DIR/uninstall-at")" ]; then
  rm -f "$MP"/com.google.Chrome.plist "$MP"/com.brave.Browser.plist "$MP"/*/com.google.Chrome.plist "$MP"/*/com.brave.Browser.plist
  sed -i '' '/# focusguard-adult-start/,/# focusguard-adult-end/d' $HOSTS
  dscacheutil -flushcache; killall -HUP mDNSResponder
  rm -rf "$DIR" "/Library/LaunchDaemons/$LABEL.plist"
  launchctl bootout system/$LABEL
  exit 0
fi

# ---- are Focus Guard and the Keeper loaded and enabled in every profile of this browser? ----
ext_ok() {  # $1 = profile root relative to the user's home
  local found=0 f
  for home in /Users/*; do
    for f in "$home/$1"/{Default,Profile\ *}/"Secure Preferences"; do
      [ -f "$f" ] || continue
      found=1
      loaded "$f" "$EXT_ID" "$EXT_DIR" && loaded "$f" "$KEEPER_ID" "$KEEPER_DIR" || return 1
    done
  done
  [ $found = 1 ]
}

loaded() {  # $1 = Secure Preferences file, $2 = extension ID, $3 = expected unpacked path
  local path reasons
  path=$(plutil -extract "extensions.settings.$2.path" raw -o - "$1" 2>/dev/null)
  reasons=$(plutil -extract "extensions.settings.$2.disable_reasons" json -o - "$1" 2>/dev/null)
  [ "$path" = "$3" ] || return 1
  case "$reasons" in ""|"[]"|0) return 0 ;; *) return 1 ;; esac
}

# The two extensions re-enable each other, so the extensions page stays usable. If either is removed,
# Shorts/Reels are hard-blocked until it's loaded again, so removing it only makes things worse.
blocklist() {  # $1 = scheme (chrome|brave), $2 = profile root
  if ext_ok "$2"; then
    echo ""
  else
    echo '"youtube.com/shorts", "facebook.com/reel", "facebook.com/reels", "facebook.com/watch/reels", "fb.watch", "facebook.com/share/r"'
  fi
}

COMMON='"RestoreOnStartup": 1, "BrowserGuestModeEnabled": false, "BrowserAddPersonEnabled": false'

# Adult filter, Brave only (owner turned it off for Chrome, 2026-10-16). Plain DNS (port 53) is
# intercepted by the ISP, so family DNS only works encrypted, inside the browser. The family DNS
# also forces Google SafeSearch.
ADULT_FILTER='"DnsOverHttpsMode": "secure", "DnsOverHttpsTemplates": "https://doh.cleanbrowsing.org/doh/adult-filter/",
  "ForceGoogleSafeSearch": true, "SafeSitesFilterBehavior": 1'

# Chrome GenAI features stay off (owner's earlier choice, used to live in this same plist).
CHROME_EXTRA='"GenAILocalFoundationalModelSettings": 1, "GeminiSettings": 1, "HelpMeWriteSettings": 2,
  "TabOrganizerSettings": 2, "HistorySearchSettings": 2, "CreateThemesSettings": 2,
  "DevToolsGenAiSettings": 2, "TabCompareSettings": 2, "AutofillPredictionSettings": 2'

# Written machine-wide and per user: running browsers only watch the per-user file
# (/Library/Managed Preferences/<user>/), so that's what applies changes without a restart.
write_policy() {  # $1 = plist name, $2 = json body
  local tmp dir u; tmp=$(mktemp)
  for u in $(ls /Users | grep -v -e '^Shared$' -e '^\.'); do mkdir -p "$MP/$u"; done
  echo "{ $2 }" | plutil -convert xml1 -o "$tmp" - || { rm -f "$tmp"; return; }
  for dir in "$MP" "$MP"/*/; do
    dir=${dir%/}
    cmp -s "$tmp" "$dir/$1" && continue
    mkdir -p "$dir"; cp "$tmp" "$dir/$1.new"; mv "$dir/$1.new" "$dir/$1"
    chown root:wheel "$dir" "$dir/$1"; chmod 755 "$dir"; chmod 644 "$dir/$1"
  done
  rm -f "$tmp"
}

# Running browsers don't pick up policy changes, so restart one whose rules changed (tabs come back via
# RestoreOnStartup). This is what makes disabling the extension hard-block Shorts/Reels within a minute.
restart_if_changed() {  # $1 = plist name, $2 = app name
  local f="$MP/$1" sum user
  sum=$(md5 -q "$f" 2>/dev/null)
  [ "$sum" = "$(cat "$DIR/state-$1" 2>/dev/null)" ] && return
  echo "$sum" > "$DIR/state-$1"
  pgrep -xq "$2" || return
  user=$(stat -f %Su /dev/console)
  launchctl asuser "$(id -u "$user")" sudo -u "$user" osascript -e "quit app \"$2\""
  for _ in 1 2 3 4 5 6 7 8 9 10; do pgrep -xq "$2" || break; sleep 1; done
  pkill -x "$2"; sleep 1
  killall cfprefsd  # else the relaunched browser can get the old policy from its cache
  launchctl asuser "$(id -u "$user")" sudo -u "$user" open -a "$2"
}

# Chrome is left unrestricted (owner's choice, 2026-10-17): only the GenAI settings. Brave keeps everything.
write_policy com.google.Chrome.plist "$CHROME_EXTRA"
write_policy com.brave.Browser.plist "$COMMON, $ADULT_FILTER, \"IncognitoModeAvailability\": 1, \"TorDisabled\": true,
  \"URLBlocklist\": [ $(blocklist brave 'Library/Application Support/BraveSoftware/Brave-Browser') ]"

restart_if_changed com.google.Chrome.plist "Google Chrome"
restart_if_changed com.brave.Browser.plist "Brave Browser"

# ---- extension files stay root-owned ----
chown -R root:wheel "$DIR"; chmod -R go-w "$DIR"

# ---- adult hosts list: off on the Mac (owner, 2026-10-16); Brave's family DNS still filters ----
if grep -q '# focusguard-adult-start' $HOSTS; then
  sed -i '' '/# focusguard-adult-start/,/# focusguard-adult-end/d' $HOSTS
  dscacheutil -flushcache; killall -HUP mDNSResponder
fi
