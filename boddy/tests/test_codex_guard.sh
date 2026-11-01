#!/bin/bash
# Checks that Codex does not start while Roblox Studio runs unless the user agrees, from the phone app
# (run-in-desktop.sh) and from the desktop launcher (codex.sh). Uses /root and /usr/share/applications like
# Boddy, so run it in a container or a throwaway system. Usage: test_codex_guard.sh /path/to/boddy
set -u
BODDY=$(cd "${1:-$(dirname "$0")/..}" && pwd)
T=$(mktemp -d); fails=0
check() { if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; fails=$((fails+1)); fi; }
mkdir -p "$T/bin" /root/.config/boddy /usr/share/applications
# Stand-ins: zenity answers $ZENITY_ANSWER (0 = OK); the launcher's command and codex record that they ran.
printf '#!/bin/bash\necho "$*" >>%s/zenity.log\nexit ${ZENITY_ANSWER:-0}\n' "$T" >"$T/bin/zenity"
printf '#!/bin/bash\necho "started confirmed=${BODDY_CODEX_CONFIRMED:-}" >>%s/codex.log\n' "$T" >"$T/bin/fake-codex-terminal"
printf '#!/bin/bash\necho "codex $*" >>%s/codex.log\n' "$T" >"$T/bin/codex"
chmod +x "$T"/bin/*
desktop=/usr/share/applications/boddy-codex.desktop; had_desktop=0; [ -e $desktop ] && had_desktop=1 && cp $desktop "$T/desktop.bak"
printf '[Desktop Entry]\nType=Application\nName=Codex\nExec=%s/bin/fake-codex-terminal\n' "$T" >$desktop
running=/root/.config/boddy/studio-running
from_phone() { rm -f "$T"/*.log; PATH="$T/bin:$PATH" ZENITY_ANSWER=$1 bash "$BODDY/run-in-desktop.sh" codex; sleep 1; }

rm -f $running
from_phone 0
check "phone: Studio not running -> Codex starts, no question" '[ -s "$T/codex.log" ] && [ ! -e "$T/zenity.log" ]'
sleep 300 & echo $! >$running
from_phone 1
check "phone: Studio running, Not now -> Codex does not start" '[ ! -e "$T/codex.log" ] && grep -q "processes are in use" "$T/zenity.log"'
from_phone 0
check "phone: Studio running, Start Codex -> starts, confirmed" 'grep -q "confirmed=1" "$T/codex.log"'
rm -f "$T"/*.log; echo n | PATH="$T/bin:$PATH" bash "$BODDY/codex.sh" >"$T/out" 2>&1
check "launcher: Studio running, answer n -> Codex does not start" '[ ! -e "$T/codex.log" ] && grep -q "Roblox Studio is running" "$T/out"'
rm -f "$T"/*.log; printf 'y\n\n' | PATH="$T/bin:$PATH" bash "$BODDY/codex.sh" >"$T/out" 2>&1
check "launcher: Studio running, answer y -> Codex starts" 'grep -q "codex --no-daemon" "$T/codex.log"'
kill "$(cat $running)"; rm -f $running "$T"/*.log; printf '\n' | PATH="$T/bin:$PATH" bash "$BODDY/codex.sh" >"$T/out" 2>&1
check "launcher: Studio not running -> Codex starts without asking" 'grep -q "codex --no-daemon" "$T/codex.log" && ! grep -q "Roblox Studio is running" "$T/out"'
# Studio's sign-in, handed back by the phone's browser through the app: goes to the Studio launcher.
had_studio=0; [ -e /opt/boddy/studio ] && had_studio=1 && cp /opt/boddy/studio "$T/studio.bak"
mkdir -p /opt/boddy; printf '#!/bin/bash\necho "$@" >%s/studio.args\n' "$T" >/opt/boddy/studio; chmod +x /opt/boddy/studio
bash "$BODDY/run-in-desktop.sh" 'roblox-studio-auth:?code=abc&state=1'; sleep 1
check "a sign-in link goes to the Studio launcher" '[ "$(cat "$T/studio.args" 2>/dev/null)" = "roblox-studio-auth:?code=abc&state=1" ]'
if [ $had_studio = 1 ]; then cp "$T/studio.bak" /opt/boddy/studio; else rm -f /opt/boddy/studio; rmdir /opt/boddy 2>/dev/null; fi
if [ $had_desktop = 1 ]; then cp "$T/desktop.bak" $desktop; else rm -f $desktop; fi
echo "$fails failed"; exit $((fails>0))
