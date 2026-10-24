#!/bin/bash
# Opens a Boddy app inside the running desktop. Argument: launcher id (e.g. claude-signin).
export DISPLAY=:1 HOME=/root XDG_RUNTIME_DIR=/tmp/runtime-root
f=/usr/share/applications/boddy-$1.desktop
[ -f "$f" ] || exit 1
cmd=$(sed -n 's/^Exec=//p' "$f" | sed 's/ %[uUfF]//')
setsid bash -c "$cmd" >/dev/null 2>&1 &
