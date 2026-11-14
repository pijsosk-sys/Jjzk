#!/bin/bash
# run.sh <mod.jar> [extra JVM args...]   e.g. -Drexsmoke.schem=/abs/file.schem -Drexsmoke.presets=FAST
set -e
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; C="$ROOT/.cache"; G="$ROOT/.runtime/game"
MOD="$1"; shift
rm -rf "$G/mods" "$G/saves" "$G/screenshots" "$G"/rexsmoke-results.txt; mkdir -p "$G/mods" "$G/saves"
cp -r "$C/world" "$G/saves/smoke"
cp "$C/mods/fabric-api.jar" "$MOD" "$G/mods/"
[ -f "$C/smoke/rexsmoke.jar" ] && cp "$C/smoke/rexsmoke.jar" "$G/mods/"
exec "$ROOT/test/runtime/launch.sh" "$G" "$@" -- --quickPlaySingleplayer smoke
