#!/bin/sh
# Creates the flat test world once, with vanilla plus Fabric only, using Fabric's client game test
# framework, and keeps it as .runtime/world for every later run.
set -e
. "$(dirname "$0")/env.sh"
[ -d "$RT/world" ] && { echo "world already at $RT/world"; exit 0; }
WORK="$RT/worldgen"
rm -rf "$WORK" && mkdir -p "$WORK/classes" "$WORK/game/mods"
javac -nowarn -proc:none -cp "$(compile_classpath)" -d "$WORK/classes" "$HERE/test/worldgen/src/rexworld/MakeWorld.java"
cp "$HERE/test/worldgen/fabric.mod.json" "$WORK/classes/"
(cd "$WORK/classes" && zip -qr "$WORK/game/mods/rexworld.jar" .)
cp "$RT/mods/fabric-api.jar" "$RT/mods/fabric-client-gametest-api-v1.jar" "$WORK/game/mods/"
cd "$WORK/game"
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" java -Xmx4G -Dfabric.client.gametest -cp "$(launch_classpath)" \
  net.fabricmc.loader.impl.launch.knot.KnotClient --version "$MC_VERSION" --gameDir "$WORK/game" --assetsDir "$RT/assets" \
  --assetIndex "$(cat "$RT/asset-index.txt")" --accessToken 0 --username Smoke > "$WORK/log.txt" 2>&1 || true
SAVE=$(ls -d "$WORK"/game/saves/*/ | head -1)
[ -n "$SAVE" ] || { echo "world creation failed, see $WORK/log.txt"; exit 1; }
cp -r "$SAVE" "$RT/world"
echo "flat world saved to $RT/world"
