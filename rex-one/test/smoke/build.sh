#!/bin/bash
# Builds the test driver mod against the REX One jar in out/.
set -e
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; C="$ROOT/.cache"; B="$C/smoke"
TR="java -jar $C/tools/tiny-remapper-0.11.1-fat.jar"
LIBS="$(ls "$C"/libs/*.jar "$C"/fabric/*.jar | tr '\n' ':')"
rm -rf "$B"; mkdir -p "$B/classes"
$TR "$ROOT/out/REX-One-1.21.11.jar" "$B/mod-named.jar" "$C/tools/yarn.tiny" intermediary named "$C/client-intermediary.jar" >/dev/null
javac -nowarn -Xlint:none -proc:none --release 21 -d "$B/classes" -cp "$C/client-named.jar:$B/mod-named.jar:$LIBS" $(find "$ROOT/test/smoke/src" -name "*.java")
(cd "$B/classes" && zip -q -r "$B/named.jar" .)
$TR "$B/named.jar" "$B/rexsmoke.jar" "$C/tools/yarn.tiny" named intermediary "$C/client-named.jar" "$B/mod-named.jar" >/dev/null
(cd "$ROOT/test/smoke" && zip -q "$B/rexsmoke.jar" fabric.mod.json rexsmoke.mixins.json)
echo "built $B/rexsmoke.jar"
