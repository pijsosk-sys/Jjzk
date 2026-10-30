#!/usr/bin/env bash
# Relay app — aapt2 + javac + d8 + apksigner, no gradle.
# Needs ANDROID_HOME (build-tools 35.0.0, platforms/android-34) and libs/bridge.aar from
# ../proxy/build-aar.sh.
set -euo pipefail

SDK="${ANDROID_HOME:?set ANDROID_HOME to your sdk root}"
BT="$SDK/build-tools/${BUILD_TOOLS:-35.0.0}"
PLATFORM="$SDK/platforms/android-34/android.jar"
OUT="build"
KS="${KEYSTORE:-$OUT/debug.keystore}"
AAR="libs/bridge.aar"

[ -f "$AAR" ] || { echo "missing $AAR — run ../proxy/build-aar.sh first" >&2; exit 1; }

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/gen" "$OUT/aar" "$OUT/lib/arm64-v8a"
unzip -qo "$AAR" -d "$OUT/aar"
cp "$OUT/aar/jni/arm64-v8a/libgojni.so" "$OUT/lib/arm64-v8a/"

"$BT/aapt2" link \
    -I "$PLATFORM" \
    --manifest AndroidManifest.xml \
    --java "$OUT/gen" \
    --min-sdk-version 24 --target-sdk-version 34 \
    -o "$OUT/unsigned.apk"

find java -name '*.java' > "$OUT/srcs.txt"
javac -nowarn --release 11 -cp "$PLATFORM:$OUT/aar/classes.jar" -d "$OUT/classes" @"$OUT/srcs.txt"

find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --lib "$PLATFORM" --min-api 24 --output "$OUT" \
    "$OUT/aar/classes.jar" @"$OUT/classes.txt"

( cd "$OUT" && zip -q -0 unsigned.apk classes.dex && zip -q -0 -r unsigned.apk lib )
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KS" ]; then
    keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
        -alias relay -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=relay" >/dev/null 2>&1
fi

"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/relay.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/relay.apk" >/dev/null

echo "built: $OUT/relay.apk  ($(du -h "$OUT/relay.apk" | cut -f1))"
