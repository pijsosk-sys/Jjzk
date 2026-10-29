#!/bin/sh
# Fetches what the sources compile against: the Minecraft 1.21.11 client remapped to Fabric
# intermediary (the names the jar's own classes use), its libraries, Fabric Loader and ASM.
# Everything lands in .cache/ and is reused on the next run.
set -e
HERE=$(cd "$(dirname "$0")/.." && pwd)
CACHE="$HERE/.cache"
VERSION=1.21.11
mkdir -p "$CACHE/libs"
cd "$CACHE"

fetch() { [ -s "$2" ] || curl -sSfL --retry 4 -o "$2" "$1"; }

fetch https://piston-meta.mojang.com/mc/game/version_manifest_v2.json manifest.json
URL=$(python3 -c "import json;print([v['url'] for v in json.load(open('manifest.json'))['versions'] if v['id']=='$VERSION'][0])")
fetch "$URL" version.json
fetch "$(python3 -c "import json;print(json.load(open('version.json'))['downloads']['client']['url'])")" client.jar
if [ ! -s client-intermediary.jar ]; then
  python3 - <<'PY'
import json
d = json.load(open('version.json'))
with open('libraries.txt', 'w') as out:
    for lib in d['libraries']:
        art = lib.get('downloads', {}).get('artifact')
        if art and 'natives' not in art['path']:
            out.write(art['url'] + '\n')
PY
  (cd libs && while read -r url; do [ -s "$(basename "$url")" ] || curl -sSfLO --retry 4 "$url"; done < ../libraries.txt)
  fetch https://maven.fabricmc.net/net/fabricmc/intermediary/$VERSION/intermediary-$VERSION-v2.jar intermediary.jar
  fetch https://maven.fabricmc.net/net/fabricmc/tiny-remapper/0.11.1/tiny-remapper-0.11.1-fat.jar tiny-remapper.jar
  unzip -oq intermediary.jar mappings/mappings.tiny
  java -jar tiny-remapper.jar client.jar client-intermediary.jar mappings/mappings.tiny official intermediary libs/*.jar
fi
fetch https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.18.4/fabric-loader-0.18.4.jar libs/fabric-loader.jar
for a in asm asm-tree; do fetch https://repo1.maven.org/maven2/org/ow2/asm/$a/9.9/$a-9.9.jar $a.jar; done
# Fabric API's lifecycle events (the builder's end-of-tick hook) and rendering events (the ghost
# renderer), compile-time only: the game gets
# Fabric API from mods/. Kept out of libs/, which the test launcher puts on the game classpath.
mkdir -p fabric
fetch https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.141.4+1.21.11/fabric-api-0.141.4+1.21.11.jar fabric/fabric-api.jar
[ -n "$(ls fabric/fabric-rendering-v1-*.jar 2>/dev/null)" ] || unzip -oqj fabric/fabric-api.jar 'META-INF/jars/fabric-lifecycle-events-v1-*.jar' 'META-INF/jars/fabric-api-base-*.jar' 'META-INF/jars/fabric-rendering-v1-*.jar' -d fabric
echo "classpath ready in $CACHE"
