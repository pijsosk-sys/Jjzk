#!/bin/sh
# Rebuild REX One from the merged jar. Needs JDK 21 and python3; place the merged build at input.jar.
set -e
ASM="--add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED"
rm -rf build patchbuild patched verify
mkdir -p build patchbuild patched verify
javac -nowarn -cp input.jar -d build $(find stubs src -name '*.java')
javac $ASM -d patchbuild patch/OnePatch.java
java  $ASM -cp patchbuild OnePatch input.jar patched
# Verification needs ow2 asm 9.9 in tools/ (asm, asm-tree, asm-util, asm-analysis).
if [ -f tools/asm.jar ]; then
  CP="tools/asm.jar:tools/asm-tree.jar:tools/asm-analysis.jar"
  javac -nowarn -cp "$CP" -d patchbuild patch/VerifyOne.java patch/TempoStub.java patch/PendingStub.java patch/DispatchTest.java patch/FlowCheck.java
  java -cp "patchbuild:$CP" VerifyOne patched verify
  java -cp "patchbuild:verify" DispatchTest
  java -cp "patchbuild:$CP" FlowCheck patched
fi
# Name gate: every intermediary name the sources reach for by string must exist in this version.
# Needs maps/ with client.txt and Fabric intermediary; see tools-src/mapidx.py.
if [ -f maps/client.txt ]; then
  python3 tools-src/checknames.py maps src | tail -1
fi
python3 build_one.py
