# Shared paths for the runtime scripts. Sourced, not run.
HERE=$(cd "$(dirname "$0")/../.." && pwd)        # rex-one/
CACHE="$HERE/.cache"                              # Minecraft client + libraries (tools/setup.sh)
RT="$HERE/.runtime"                               # launcher libraries, mods, assets, world
MC_VERSION=1.21.11
LOADER=0.18.4
FABRIC_API=0.141.4+1.21.11
GAMETEST_API=4.3.5+bfe9fcf43e
export JAVA_TOOL_OPTIONS=
launch_classpath() {
  echo "$(ls "$RT"/libs/*.jar | tr '\n' ':')$(ls "$CACHE"/libs/*.jar | grep -v fabric-loader | tr '\n' ':')$CACHE/client.jar"
}
compile_classpath() {
  echo "$CACHE/client-intermediary.jar:$(ls "$CACHE"/libs/*.jar | tr '\n' ':')$(ls "$RT"/fapi/META-INF/jars/*.jar | tr '\n' ':')$RT/mods/fabric-client-gametest-api-v1.jar"
}
