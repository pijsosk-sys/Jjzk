#!/bin/bash
# Starts the desktop with start-desktop.sh for real (Xvnc on :1), with stand-ins for pulseaudio and the XFCE
# session, and checks the colour depth chosen. Takes over :1 and writes /etc/pulse, /etc/profile.d and /tmp
# like Boddy does: run it in a container. Usage: test_start_desktop.sh /path/to/boddy
set -u
BODDY=$(cd "${1:-$(dirname "$0")/..}" && pwd)
T=$(mktemp -d); fails=0
check() { if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; fails=$((fails+1)); fi; }
mkdir -p "$T/bin"
printf '#!/bin/sh\nexit 0\n' >"$T/bin/pulseaudio"
printf '#!/bin/sh\nexec sleep 30\n' >"$T/bin/dbus-launch"  # the XFCE session: keeps the script running
chmod +x "$T"/bin/*
stop() { for p in $(pgrep -x Xvnc); do ps -o args= -p "$p" | grep -q '^Xvnc :1 ' && kill "$p"; done; sleep 1; }
run() {  # DEPTH_SETTING: what /root/.config/boddy/vnc-depth holds ('' for none)
    stop; mkdir -p /root/.config/boddy
    if [ -n "$1" ]; then echo "$1" >/root/.config/boddy/vnc-depth; else rm -f /root/.config/boddy/vnc-depth; fi
    PATH="$T/bin:$PATH" bash "$BODDY/start-desktop.sh" 1024 600 >"$T/out" 2>&1 & S=$!
    for _ in $(seq 50); do DISPLAY=:1 xdpyinfo >/dev/null 2>&1 && break; sleep 0.2; done
    depth=$(DISPLAY=:1 xdpyinfo 2>/dev/null | sed -n 's/.*depth of root window: *\([0-9]*\).*/\1/p')
    args=$(ps -o args= -p "$(pgrep -x Xvnc | while read p; do ps -o args= -p $p | grep -q '^Xvnc :1 ' && echo $p; done | head -1)")
}
had=$(cat /root/.config/boddy/vnc-depth 2>/dev/null)
run ""
check "default: 24-bit desktop" '[ "$depth" = 24 ] && [ "$(cat /tmp/boddy-vnc-depth)" = 24 ]'
check "Xvnc takes -ZlibLevel=1" 'grep -q -- "-ZlibLevel=1" <<<"$args"'
run 16
check "16-bit chosen: 16-bit desktop" '[ "$depth" = 16 ] && [ "$(cat /tmp/boddy-vnc-depth)" = 16 ] && grep -q rgb565 <<<"$args"'
run nonsense
check "anything else: 24-bit" '[ "$depth" = 24 ]'
kill $S 2>/dev/null; stop
if [ -n "$had" ]; then echo "$had" >/root/.config/boddy/vnc-depth; else rm -f /root/.config/boddy/vnc-depth; fi
echo "$fails failed"; exit $((fails>0))
