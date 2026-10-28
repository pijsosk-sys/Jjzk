#!/bin/sh
# Launches the real game with a REX One jar and the smoke driver, straight into the flat world.
#   run.sh <rex jar> <schematic> [-Drexsmoke.presets=FAST,OFF -Drexsmoke.creative=true -Drexsmoke.maxSeconds=300 ...]
# Results: .runtime/game/rexsmoke-results.txt, screenshots in .runtime/game/screenshots/.
set -e
. "$(dirname "$0")/env.sh"
REX=$(cd "$(dirname "$1")" && pwd)/$(basename "$1"); SCHEM=$(cd "$(dirname "$2")" && pwd)/$(basename "$2"); shift 2
GAME="$RT/game"
rm -rf "$GAME" "$RT/driver" && mkdir -p "$GAME/mods" "$GAME/saves" "$RT/driver"
javac -nowarn -proc:none -cp "$REX:$(compile_classpath)" -d "$RT/driver" "$HERE/test/smoke/src/rexsmoke/SmokeDriver.java"
cp "$HERE/test/smoke/fabric.mod.json" "$RT/driver/"
(cd "$RT/driver" && zip -qr "$GAME/mods/rexsmoke.jar" .)
cp "$REX" "$GAME/mods/rex-one.jar"
cp "$RT/mods/fabric-api.jar" "$GAME/mods/"
cp -r "$RT/world" "$GAME/saves/smoke"
# Skip the first-launch accessibility prompt, which would sit in front of quick play.
printf "onboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\nrenderDistance:5\nsimulationDistance:5\nmaxFps:60\n" > "$GAME/options.txt"
cd "$GAME"
exec env LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" java -Xmx4G -Drexsmoke.schem="$SCHEM" "$@" -cp "$(launch_classpath)" \
  net.fabricmc.loader.impl.launch.knot.KnotClient --version "$MC_VERSION" --gameDir "$GAME" --assetsDir "$RT/assets" \
  --assetIndex "$(cat "$RT/asset-index.txt")" --accessToken 0 --username Smoke --uuid 00000000-0000-0000-0000-000000000001 \
  --width 1280 --height 720 --quickPlaySingleplayer smoke
