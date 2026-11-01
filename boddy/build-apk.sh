#!/bin/bash
# Rebuilds the Boddy APK from a released one with this folder's changes:
#   - assets/boddy/: studio, studio-app.sh, studio-mode.sh, start-desktop.sh, run-in-desktop.sh, codex.sh
#   - classes.dex: ZRLE in the VNC client (vnc/Zrle.java + vnc/patch_rfbclient.py)
# Every other entry is copied unchanged, stored the same way (compressed or not) as in the original.
#
#   build-apk.sh ORIGINAL.apk OUT_DIR TOOLS_DIR [KEYSTORE ALIAS STOREPASS]
# TOOLS_DIR holds Android build-tools (d8, zipalign, apksigner) and baksmali.jar / smali.jar (2.5.2).
# Writes OUT_DIR/Boddy-unsigned.apk (aligned, for signing with Boddy's own key) and, with a keystore,
# OUT_DIR/Boddy.apk signed with it. Only an APK signed with the same key as the installed Boddy installs
# over it; another key means uninstalling Boddy first, which deletes its Linux system.
set -euo pipefail
ORIG=$(realpath "$1") OUT=$(realpath -m "$2") TOOLS=$(realpath "$3")
HERE=$(cd "$(dirname "$0")" && pwd)
BT=$(dirname "$(find "$TOOLS" -name d8 -type f | head -n 1)")
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
mkdir -p "$OUT"
java() { command java "$@" 2> >(grep -v JAVA_TOOL_OPTIONS >&2); }

echo "== VNC client: ZRLE"
unzip -q -o "$ORIG" classes.dex -d "$W/orig"
java -jar "$TOOLS/baksmali.jar" d "$W/orig/classes.dex" -o "$W/smali"
mkdir -p "$W/zrle/stub/com/boddy/os/vnc" "$W/zrle/classes"
# Zrle uses only RfbClient's public getters; a stand-in with them is enough to compile it.
cat >"$W/zrle/stub/com/boddy/os/vnc/RfbClient.java" <<'JAVA'
package com.boddy.os.vnc;
public final class RfbClient {
    public Object getLock() { return null; } public int[] getPixels() { return null; }
    public int getWidth() { return 0; } public int getHeight() { return 0; } }
JAVA
javac -Xlint:-options --release 8 -d "$W/zrle/classes" -sourcepath "$W/zrle/stub" "$HERE/vnc/Zrle.java" 2> >(grep -v JAVA_TOOL_OPTIONS >&2)
"$BT/d8" --release --min-api 26 --output "$W/zrle" "$W/zrle/classes/com/boddy/os/vnc/Zrle.class"
java -jar "$TOOLS/baksmali.jar" d "$W/zrle/classes.dex" -o "$W/zrle/smali"
python3 "$HERE/vnc/patch_rfbclient.py" "$W/smali" "$W/zrle/smali/com/boddy/os/vnc/Zrle.smali"
java -jar "$TOOLS/smali.jar" a --api 26 "$W/smali" -o "$W/classes.dex"

echo "== Assets and APK"
python3 - "$ORIG" "$W/unaligned.apk" "$W/classes.dex" "$HERE" <<'PY'
import sys,zipfile,os
orig,out,dex,here=sys.argv[1:]
replace={'classes.dex':dex}
for name in ('studio','studio-app.sh','studio-mode.sh','start-desktop.sh','run-in-desktop.sh','codex.sh'):
    replace['assets/boddy/'+name]=os.path.join(here,name)
with zipfile.ZipFile(orig) as src,zipfile.ZipFile(out,'w') as dst:
    for info in src.infolist():
        # The old signature goes; apksigner writes the new one.
        if info.filename.startswith('META-INF/') and info.filename.rsplit('.',1)[-1] in ('SF','RSA','DSA','EC','MF'):continue
        data=open(replace.pop(info.filename),'rb').read() if info.filename in replace else src.read(info.filename)
        new=zipfile.ZipInfo(info.filename,date_time=info.date_time)
        new.compress_type=info.compress_type;new.external_attr=info.external_attr
        dst.writestr(new,data)
    if replace:sys.exit('Not in the original APK: '+', '.join(replace))
PY
# -p: shared libraries page-aligned, as Android needs to load them straight from the APK.
"$BT/zipalign" -f -p 4 "$W/unaligned.apk" "$OUT/Boddy-unsigned.apk"
"$BT/zipalign" -c -p 4 "$OUT/Boddy-unsigned.apk"
echo "Unsigned: $OUT/Boddy-unsigned.apk"
if [ $# -ge 6 ]; then
    "$BT/apksigner" sign --ks "$4" --ks-key-alias "$5" --ks-pass "pass:$6" --out "$OUT/Boddy.apk" "$OUT/Boddy-unsigned.apk"
    "$BT/apksigner" verify --print-certs "$OUT/Boddy.apk" | grep -E "Signer #1 certificate (DN|SHA-256)"
    echo "Signed: $OUT/Boddy.apk"
fi
