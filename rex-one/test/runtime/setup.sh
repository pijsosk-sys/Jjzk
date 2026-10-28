#!/bin/sh
# Downloads what it takes to launch the real game: Fabric's launcher libraries, the LWJGL natives,
# Fabric API, the client game test API, and the game assets minus sounds and music. Reuses
# .cache/ from tools/setup.sh for the client jar and its libraries.
set -e
. "$(dirname "$0")/env.sh"
[ -s "$CACHE/client.jar" ] || "$HERE/tools/setup.sh"
mkdir -p "$RT/libs" "$RT/mods" "$RT/assets/indexes" "$RT/assets/objects"
cd "$RT"
fetch() { [ -s "$2" ] || curl -sSfL --retry 4 -o "$2" "$1"; }

fetch "https://meta.fabricmc.net/v2/versions/loader/$MC_VERSION/$LOADER/profile/json" profile.json
python3 - "$CACHE/version.json" <<'PY' > urls.txt
import json, sys
profile = json.load(open('profile.json'))
for lib in profile['libraries']:
    group, name, version = lib['name'].split(':')
    print(f"{lib['url']}{group.replace('.', '/')}/{name}/{version}/{name}-{version}.jar")
for lib in json.load(open(sys.argv[1]))['libraries']:
    art = lib.get('downloads', {}).get('artifact')
    if art and 'natives-linux' in art['path'] and 'arm' not in art['path']:
        print(art['url'])
PY
(cd libs && while read -r url; do [ -s "$(basename "$url")" ] || curl -sSfLO --retry 4 "$url"; done < ../urls.txt)

fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$FABRIC_API/fabric-api-$FABRIC_API.jar" mods/fabric-api.jar
fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-client-gametest-api-v1/$GAMETEST_API/fabric-client-gametest-api-v1-$GAMETEST_API.jar" mods/fabric-client-gametest-api-v1.jar
[ -d fapi ] || (mkdir fapi && cd fapi && unzip -q ../mods/fabric-api.jar)

INDEX=$(python3 -c "import json;d=json.load(open('$CACHE/version.json'))['assetIndex'];print(d['id'], d['url'])")
set -- $INDEX
fetch "$2" "assets/indexes/$1.json"
echo "$1" > asset-index.txt
python3 - "assets/indexes/$1.json" <<'PY' | (cd assets/objects && xargs -P16 -n2 sh -c 'mkdir -p "$(dirname "$1")"; [ -s "$1" ] || curl -sSfL --retry 3 -o "$1" "$0"')
import json, sys
for key, obj in json.load(open(sys.argv[1]))['objects'].items():
    if key.startswith(('minecraft/sounds/', 'minecraft/music')) or key.endswith('.ogg'):
        continue
    h = obj['hash']
    print(f"https://resources.download.minecraft.net/{h[:2]}/{h} {h[:2]}/{h}")
PY
echo "runtime ready in $RT"
