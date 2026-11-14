#!/bin/bash
# Creates the flat test world once (vanilla + Fabric API only) and keeps it as .cache/world.
set -e
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; C="$ROOT/.cache"; G="$C/worldgen-game"
[ -d "$C/world" ] && { echo "world exists"; exit 0; }
rm -rf "$G" "$C/worldgen-build"; mkdir -p "$G/mods" "$C/worldgen-build"
javac -nowarn -d "$C/worldgen-build" -cp "$C/tools/gametest-api.jar" "$ROOT"/test/worldgen/src/rexworldgen/*.java
cp "$ROOT/test/worldgen/fabric.mod.json" "$C/worldgen-build/"
(cd "$C/worldgen-build" && zip -q -r "$G/mods/rexworldgen.jar" .)
cp "$C/mods/fabric-api.jar" "$C/tools/gametest-api.jar" "$G/mods/"
"$ROOT/test/runtime/launch.sh" "$G" -Dfabric.client.gametest -Dfabric.client.gametest.disableNetworkSynchronizer=true > "$C/worldgen.log" 2>&1 || true
W="$(ls -d "$G"/saves/*/ 2>/dev/null | head -1)"
[ -n "$W" ] || { tail -40 "$C/worldgen.log"; echo "no world created"; exit 1; }
cp -r "$W" "$C/world"; echo "world saved to $C/world"
