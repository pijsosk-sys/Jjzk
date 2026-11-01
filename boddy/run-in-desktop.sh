#!/bin/bash
# Opens a Boddy app inside the running desktop. Argument: launcher id (e.g. claude-signin).
export DISPLAY=:1 HOME=/root XDG_RUNTIME_DIR=/tmp/runtime-root
f=/usr/share/applications/boddy-$1.desktop
[ -f "$f" ] || exit 1
cmd=$(sed -n 's/^Exec=//p' "$f" | sed 's/ %[uUfF]//')
# While Roblox Studio runs, every process counts against Android's limit of 32 (Studio and Wine need about 15
# on top of Boddy's own). Codex is not needed for Studio and its start (Node, and npm the first time) adds
# several, so it only starts then when the user says so; the question shows how many are in use.
running=/root/.config/boddy/studio-running
if [ "$1" = codex ] && [ -e "$running" ] && kill -0 "$(cat "$running" 2>/dev/null)" 2>/dev/null; then
    n=$(ls -d /proc/[0-9]* 2>/dev/null | wc -l)
    cmd="zenity --question --width=440 --title=Codex --ok-label='Start Codex' --cancel-label='Not now' \
--text='Roblox Studio is running and $n of 32 processes are in use. Codex adds several more, and Android may then close Boddy. Start Codex anyway?' \
2>/dev/null && BODDY_CODEX_CONFIRMED=1 $cmd"
fi
setsid bash -c "$cmd" >/dev/null 2>&1 &
