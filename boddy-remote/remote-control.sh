#!/bin/bash
# Keeps `claude remote-control` running. When it exits for any reason it starts again after a
# short pause, so a crash or lost connection never leaves Claude Remote off.
DIR=/root/.boddy/remote
LOG=$DIR/remote.log
export HOME=/root DISPLAY=${DISPLAY:-:1} XDG_RUNTIME_DIR=/tmp/runtime-root
export PATH="/opt/boddy/phone-bin:/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
export BROWSER=/opt/boddy/open-on-phone
[ -S /tmp/pulse.sock ] && export PULSE_SERVER=unix:/tmp/pulse.sock
mkdir -p "$DIR" /root/Projects
cd /root/Projects || cd /root

# Only one copy. A second one (e.g. from the watchdog) quietly leaves.
exec 8>/tmp/boddy-remote.lock
if ! flock -n 8; then
    echo "Claude Remote is already running."
    sleep 3
    exit 0
fi
trap '' HUP   # closing the window must not stop it

log() { echo "$(date '+%F %T') $*" >> "$LOG"; }
# Keep the log small.
[ "$(stat -c %s "$LOG" 2>/dev/null || echo 0)" -gt 200000 ] && tail -n 300 "$LOG" > "$LOG.new" && mv "$LOG.new" "$LOG"

pause=5
while [ -e /root/.config/boddy/remote-on ]; do
    if ! command -v claude >/dev/null; then
        echo "Installing Claude Code..."
        log "installing Claude Code"
        curl -fsSL https://claude.ai/install.sh | bash || log "install failed"
    fi
    if command -v claude >/dev/null; then
        clear
        cat <<'MSG'
  Claude Remote
  Open the Claude app on your phone (or claude.ai/code), go to Code, and pick this
  Boddy session. Chat there; Claude works here on the Boddy desktop.

  First time: if it asks to sign in, open the "Claude Code" icon once and sign in.
  You can close this window; Claude Remote keeps running in the background.

MSG
        log "starting claude remote-control"
        started=$(date +%s)
        claude remote-control
        code=$?
        log "claude remote-control exited ($code)"
        # Ran a while: it was working, restart quickly. Died at once: back off up to a minute.
        if [ $(( $(date +%s) - started )) -gt 120 ]; then pause=5; else pause=$(( pause * 2 > 60 ? 60 : pause * 2 )); fi
    fi
    echo
    echo "Claude Remote stopped; starting again in $pause s (run 'boddy-remote stop' to turn it off)."
    sleep "$pause"
done
log "turned off"
