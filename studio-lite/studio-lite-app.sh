#!/bin/bash
# Desktop icon for Studio Lite: starts its local server (once) and opens it in Firefox.
APP=${STUDIO_LITE_HOME:-$(dirname "$(readlink -f "$0")")}
DATA=$HOME/.local/share/studio-lite
mkdir -p "$DATA"
URLFILE=$DATA/url
LOG=$DATA/server.log

alive() { [ -s "$URLFILE" ] && curl -fsS -o /dev/null -X POST "$(cut -d'#' -f1 "$URLFILE")api/list" -H "X-Token: $(cut -d'#' -f2 "$URLFILE")"; }

if ! alive; then
    rm -f "$URLFILE"
    nohup python3 "$APP/server.py" > "$LOG.tmp" 2> "$LOG" < /dev/null &
    for _ in $(seq 50); do
        head -1 "$LOG.tmp" 2>/dev/null | grep -q '^http://127.0.0.1:' && { head -1 "$LOG.tmp" > "$URLFILE"; break; }
        sleep 0.2
    done
    chmod 600 "$URLFILE" 2>/dev/null
fi
if ! alive; then
    zenity --error --width=420 --title="Studio Lite" --text="Studio Lite didn't start. Details are in $LOG" 2>/dev/null
    exit 1
fi

# Its own Firefox profile: WebGL on even with software rendering, and no restoring of old tabs.
PROFILE=$DATA/firefox-profile
mkdir -p "$PROFILE"
cat > "$PROFILE/user.js" <<'PREFS'
user_pref("webgl.force-enabled", true);
user_pref("webgl.disabled", false);
user_pref("browser.sessionstore.resume_from_crash", false);
user_pref("browser.startup.page", 0);
user_pref("browser.shell.checkDefaultBrowser", false);
user_pref("browser.aboutwelcome.enabled", false);
user_pref("datareporting.policy.dataSubmissionEnabled", false);
user_pref("toolkit.telemetry.enabled", false);
PREFS
# Firefox's sandboxes don't work under proot (the Boddy desktop).
export MOZ_FAKE_NO_SANDBOX=1 MOZ_DISABLE_CONTENT_SANDBOX=1 MOZ_DISABLE_GMP_SANDBOX=1 MOZ_DISABLE_RDD_SANDBOX=1 MOZ_DISABLE_SOCKET_PROCESS_SANDBOX=1
FIREFOX=$(command -v firefox-esr || command -v firefox)
exec "$FIREFOX" --profile "$PROFILE" --new-window "$(cat "$URLFILE")" > "$DATA/firefox.log" 2>&1
