#!/bin/sh
# Builds REX One from the released jar plus the sources in src/.
#   ./build.sh path/to/REX-One-1.21.11.jar      -> out/REX-One-1.21.11.jar
# Needs JDK 21, python3 and curl (first run only, for tools/setup.sh).
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
BASE=${1:?usage: build.sh <base REX-One jar>}
VERSION=1.0.0-one.18
CACHE="$HERE/.cache"
WORK="$HERE/.work"
OUT="$HERE/out/REX-One-1.21.11.jar"

[ -s "$CACHE/client-intermediary.jar" ] && [ -s "$CACHE/asm-tree.jar" ] && [ -s "$CACHE/fabric/fabric-api.jar" ] || "$HERE/tools/setup.sh"
CP="$BASE:$CACHE/client-intermediary.jar:$(ls "$CACHE"/libs/*.jar "$CACHE"/fabric/fabric-*-v1-*.jar "$CACHE"/fabric/fabric-api-base-*.jar | tr '\n' ':')"

rm -rf "$WORK" && mkdir -p "$WORK/classes" "$WORK/tools" "$HERE/out"
echo "compiling $(find "$HERE/src" -name '*.java' | wc -l) sources against the base jar and Minecraft $CP" | cut -c1-120
javac --release 21 -nowarn -proc:none -encoding UTF-8 -cp "$CP" -d "$WORK/classes" $(find "$HERE/src" -name '*.java')

# The classes this build replaces, as internal names.
REPLACED=$(cd "$HERE/src" && find . -name '*.java' | sed 's|^\./||; s|\.java$||')

python3 - "$BASE" "$OUT" "$WORK/classes" "$HERE/src" "$HERE/notes/build-tempo.txt" "$VERSION" $REPLACED <<'PY'
import json, re, sys, zipfile
from pathlib import Path

base, out, classes, src, notes, version, *replaced = sys.argv[1:]
classes, src = Path(classes), Path(src)
built = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}

def owned(name):
    # A replaced class and every nested class of it come from this build, so stale nested
    # classes of the old version (records and enums that no longer exist) are dropped.
    stem = name[:-6].split('$')[0]
    return stem in replaced

with zipfile.ZipFile(base) as zin, zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zout:
    dropped = 0
    shipped = {'one-client-source/src/' + p.relative_to(src).as_posix() for p in src.rglob('*.java')}
    for item in zin.infolist():
        name = item.filename
        if name.endswith('.class') and owned(name):
            dropped += 1
            continue
        if name in shipped or name.startswith('assets/rex/post_effect/') or name.startswith('assets/rex/shaders/post/'):
            continue
        data = zin.read(name)
        if name == 'fabric.mod.json':
            meta = json.loads(data)
            meta['version'] = version
            meta['description'] = ("One client: a single guarded entry point for the merged Donut, Krypton and REX subsystems, "
                                   "one menu key, snapshot Light ESP and a pipelined Auto Build with speed presets, "
                                   "a redesigned builder screen and automatic retry.")
            data = (json.dumps(meta, indent=2) + '\n').encode()
        elif name == 'assets/rex/schematics/kelp-blue-behemoth.litematic':
            # Saved by a newer Minecraft (data version 4790), so 1.21.11 (4671) refused to load it.
            # Its block states all exist in 1.21.11; it loads and builds there once stamped 4671.
            import gzip, struct
            raw = gzip.decompress(data)
            for key in (b'MinecraftDataVersion', b'DataVersion'):
                head = bytes([3]) + struct.pack('>H', len(key)) + key
                raw = raw.replace(head + struct.pack('>i', 4790), head + struct.pack('>i', 4671))
            data = gzip.compress(raw, mtime=0)
        elif name == 'README.txt':
            text = data.decode('utf-8')
            text = re.sub(r'version 1\.0\.0-one\.\d+', 'version ' + version, text)
            start = text.index('Build Tempo (Misc)')
            end = text.index('What did not change')
            text = text[:start] + Path(notes).read_text() + '\n' + text[end:]
            text = text.replace('  still one placement in flight at a time, so ping, not the client, bounds large builds.\n',
                                '  only pipelined when a preset trusts the prediction; Safe still waits on every block.\n')
            data = text.encode('utf-8')
        zout.writestr(item, data)
    for name, data in sorted(built.items()):
        info = zipfile.ZipInfo(name, date_time=(2026, 10, 28, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(info, data)
    # Assets this build adds (the Tuff Shader post effect and its fragment shader).
    resources = Path(src).parent / 'resources'
    for path in sorted(resources.rglob('*')):
        if path.is_file():
            info = zipfile.ZipInfo(path.relative_to(resources).as_posix(), date_time=(2026, 10, 28, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            zout.writestr(info, path.read_bytes())
    # Ship the sources next to the ones the jar already carries.
    for path in sorted(src.rglob('*.java')):
        info = zipfile.ZipInfo('one-client-source/src/' + path.relative_to(src).as_posix(), date_time=(2026, 10, 28, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(info, path.read_bytes())
print(f"replaced {dropped} classes with {len(built)} built ones -> {out}")
PY

javac -nowarn -cp "$CACHE/asm.jar:$CACHE/asm-tree.jar" -d "$WORK/tools" "$HERE/tools/LinkCheck.java" "$HERE/tools/FixMixinAt.java"
java -cp "$WORK/tools:$CACHE/asm.jar:$CACHE/asm-tree.jar" FixMixinAt "$OUT"
java -cp "$WORK/tools:$CACHE/asm.jar:$CACHE/asm-tree.jar" LinkCheck "$OUT" $REPLACED
echo "built $OUT"
