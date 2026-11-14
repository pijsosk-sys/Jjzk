#!/bin/bash
# build.sh [base.jar]  ->  out/REX-One-1.21.11.jar
# Sources in src/ use readable (yarn) names; they are compiled against the game and the base jar,
# remapped to Fabric's intermediary names, and laid over the base jar together with resources/.
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"; C="$ROOT/.cache"; B="$C/build"
BASE="${1:-$ROOT/base/Donut-REX-AutoBuild-1.21.11-fix63.jar}"
TR="java -jar $C/tools/tiny-remapper-0.11.1-fat.jar"
ASM="$(ls "$C"/fabric/asm-*.jar | tr '\n' ':')"
LIBS="$(ls "$C"/libs/*.jar "$C"/fabric/*.jar | tr '\n' ':')"
rm -rf "$B"; mkdir -p "$B/stage" "$B/classes" "$B/tools" "$ROOT/out"
[ -f "$C/client-named.jar" ] || $TR "$C/client-intermediary.jar" "$C/client-named.jar" "$C/tools/yarn.tiny" intermediary named >/dev/null
if [ ! -f "$C/mod-named.jar" ] || [ "$BASE" -nt "$C/mod-named.jar" ]; then
  $TR "$BASE" "$C/mod-named.jar" "$C/tools/yarn.tiny" intermediary named "$C/client-intermediary.jar" >/dev/null
fi
(cd "$B/stage" && unzip -q -o "$BASE")
javac -nowarn -d "$B/tools" -cp "$ASM" "$ROOT/tools/FixMixins.java"
java -cp "$B/tools:$ASM" FixMixins "$B/stage"
if [ -n "$(find "$ROOT/src" -name '*.java' 2>/dev/null | head -1)" ]; then
  javac -nowarn -Xlint:none -proc:none --release 21 -d "$B/classes" -cp "$C/client-named.jar:$C/mod-named.jar:$LIBS" $(find "$ROOT/src" -name '*.java')
  (cd "$B/classes" && zip -q -r "$B/classes-named.jar" .)
  $TR "$B/classes-named.jar" "$B/classes-int.jar" "$C/tools/yarn.tiny" named intermediary "$C/client-named.jar" "$C/mod-named.jar" >/dev/null
  (cd "$B/stage" && unzip -q -o "$B/classes-int.jar")
fi
[ -d "$ROOT/resources" ] && cp -r "$ROOT/resources/." "$B/stage/"
[ -f "$ROOT/tools/poststage.py" ] && python3 "$ROOT/tools/poststage.py" "$B/stage"
rm -f "$ROOT/out/REX-One-1.21.11.jar"
(cd "$B/stage" && zip -q -r -X "$ROOT/out/REX-One-1.21.11.jar" META-INF/MANIFEST.MF && zip -q -r -X "$ROOT/out/REX-One-1.21.11.jar" . -x META-INF/MANIFEST.MF)
ls -la "$ROOT/out/REX-One-1.21.11.jar"
