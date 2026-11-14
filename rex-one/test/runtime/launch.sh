#!/bin/bash
# launch.sh <gameDir> [extra JVM args...] -- [extra game args...]
# Starts the real Minecraft client through Fabric's Knot launcher on a virtual display.
set -e
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; C="$ROOT/.cache"
GAME="$1"; shift
JVM=(); while [ $# -gt 0 ] && [ "$1" != "--" ]; do JVM+=("$1"); shift; done; [ "$1" = "--" ] && shift
mkdir -p "$GAME" "$C/natives"
[ -f "$GAME/options.txt" ] || printf 'onboardAccessibility:false\ntutorialStep:none\nnarrator:0\nmaxFps:60\nrenderDistance:6\nsimulationDistance:6\npauseOnLostFocus:false\n' > "$GAME/options.txt"
CP="$(ls "$C"/fabric/*.jar "$C"/libs/*.jar | tr '\n' ':')$C/client.jar"
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5
exec xvfb-run -a -s "-screen 0 1280x720x24" java -Xmx3G -XX:+UseG1GC \
  -Djava.library.path="$C/natives" -Dorg.lwjgl.librarypath="$C/natives" -Dfabric.skipMcProvider=false \
  "${JVM[@]}" -cp "$CP" "$(cat "$C/fabric-main.txt")" \
  --version 1.21.11 --gameDir "$GAME" --assetsDir "$C/assets" --assetIndex "$(cat "$C/asset-index.txt")" \
  --accessToken 0 --username Smoke --width 1280 --height 720 "$@"
