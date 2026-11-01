#!/bin/bash
# Checks the launcher runs as one process (its shell script becomes Python, which writes the log itself: no
# bash or tee alongside it), still writes its log and exit status, and that opening the sign-in Firefox warns
# when Android's process limit would be passed. Stand-ins for Firefox and zenity.
# Usage: test_launcher_processes.sh /path/to/studio
set -u
STUDIO=$(realpath "$1"); T=$(mktemp -d); fails=0
check() { if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; fails=$((fails+1)); fi; }
export HOME=$T/home; mkdir -p "$HOME" "$T/bin"
printf '#!/bin/sh\nsleep 4\n' >"$T/bin/firefox-esr"
printf '#!/bin/sh\necho "$*" >>%s/zenity.log\nexit ${ZENITY_ANSWER:-0}\n' "$T" >"$T/bin/zenity"
chmod +x "$T"/bin/*
export PATH="$T/bin:$PATH"

BODDY_STUDIO_STATUS=$T/status bash "$STUDIO" --use-preset=Potato >"$T/out" 2>&1
check "a launcher run prints its result and log path" 'grep -q "Potato preset" "$T/out" && grep -q "^Launcher log: " "$T/out"'
log=$(sed -n 's/^Launcher log: //p' "$T/out")
check "the log holds what it printed" 'grep -q "Potato preset" "$log"'
check "exit status 0 written for studio-app.sh" '[ "$(cat "$T/status")" = 0 ]'
BODDY_STUDIO_STATUS=$T/status bash "$STUDIO" --use-preset=Ultra >"$T/out" 2>&1; code=$?
check "a failure: LAUNCH FAILED, exit 1, status 1" 'grep -q "LAUNCH FAILED" "$T/out" && [ $code = 1 ] && [ "$(cat "$T/status")" = 1 ]'

# The sign-in helper while it waits on Firefox: which processes is it?
ZENITY_ANSWER=0 bash "$STUDIO" --open-url https://www.roblox.com/login >"$T/out" 2>&1 & pid=$!
sleep 2
tree=$(ps -o comm= --ppid $pid; ps -o comm= -p $pid)
echo "     sign-in helper processes: $(echo $tree)"
check "sign-in helper is python3 + Firefox only (no bash, no tee)" '[ "$(ps -o comm= -p $pid)" = python3 ] && ! grep -q -x -e tee -e bash <<<"$tree"'
wait $pid
n=$(ls -d /proc/[0-9]* | wc -l)
if [ "$n" -gt 29 ]; then  # this machine is over Android's limit already: the warning shows
    check "over the limit: the sign-in warns first" 'grep -q "Disable child process restrictions" "$T/zenity.log"'
    rm -f "$T/zenity.log"; ZENITY_ANSWER=1 bash "$STUDIO" --open-url https://www.roblox.com/login >"$T/out" 2>&1
    check "Not now: Firefox is not opened" 'grep -q "Sign-in not started" "$T/out" && ! grep -q "Opening in Firefox" "$T/out"'
fi
echo "$fails failed"; exit $((fails>0))
