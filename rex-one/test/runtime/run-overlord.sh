#!/bin/sh
# Launches the real game with a REX One jar and the Overlord test driver, straight into the flat
# world, and runs the Overlord scenarios in it.
#   run-overlord.sh <rex jar> [-Drexoverlord.scenarios=tools,mine ...]
# Results: $GAME_DIR/rexoverlord-results.txt, screenshots in $GAME_DIR/screenshots/.
set -e
. "$(dirname "$0")/env.sh"
REX=$(cd "$(dirname "$1")" && pwd)/$(basename "$1"); shift 1
GAME="${GAME_DIR:-$RT/game}"   # GAME_DIR lets two runs go side by side
DRIVER="$GAME.driver"
rm -rf "$GAME" "$DRIVER" && mkdir -p "$GAME/mods" "$GAME/saves" "$DRIVER"
javac -nowarn -proc:none -cp "$REX:$(compile_classpath)" -d "$DRIVER" "$HERE"/test/overlord/src/rexoverlord/*.java
cp "$HERE/test/overlord/fabric.mod.json" "$DRIVER/"
(cd "$DRIVER" && zip -qr "$GAME/mods/rexoverlordtest.jar" .)
cp "$REX" "$GAME/mods/rex-one.jar"
cp "$RT/mods/fabric-api.jar" "$GAME/mods/"
cp -r "$RT/world" "$GAME/saves/smoke"
# Skip the first-launch accessibility prompt, which would sit in front of quick play.
printf "onboardAccessibility:false\ntutorialStep:none\nskipMultiplayerWarning:true\njoinedFirstServer:true\nrenderDistance:5\nsimulationDistance:5\nmaxFps:60\n" > "$GAME/options.txt"
cd "$GAME"
exec env LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" java -Xmx4G -Drexoverlord.run=true "$@" -cp "$(launch_classpath)" \
  net.fabricmc.loader.impl.launch.knot.KnotClient --version "$MC_VERSION" --gameDir "$GAME" --assetsDir "$RT/assets" \
  --assetIndex "$(cat "$RT/asset-index.txt")" --accessToken 0 --username Smoke --uuid 00000000-0000-0000-0000-000000000001 \
  --width 1280 --height 720 --quickPlaySingleplayer smoke
