#!/bin/bash
# Builds Roblox Publish without Gradle: aapt2 for resources, javac + d8 for code, then sign.
#   android.jar  from the Debian package android-sdk-platform-23
#   d8           from Google's Maven (com.android.tools:r8), run on the JVM
#   aapt2        from the Debian package aapt
#   zipalign, apksigner  from the Debian packages of the same name
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=${1:-$HERE/build}
ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
R8_JAR=${R8_JAR:?set R8_JAR to r8.jar}
KEYSTORE=${KEYSTORE:-$OUT/signing.jks}
STOREPASS=${STOREPASS:-robloxpublish}

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/res"

echo "[1/5] resources"
aapt2 compile --dir "$HERE/res" -o "$OUT/res.zip" 2>/dev/null || : # no res/ yet: the UI is all code
aapt2 link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
    --manifest "$HERE/AndroidManifest.xml" --java "$OUT/gen" \
    ${OUT_RES:-} $( [ -f "$OUT/res.zip" ] && echo "$OUT/res.zip" )

echo "[2/5] javac"
find "$HERE/src" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
    -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v 'bootstrap class path\|source value 8\|target value 8\|deprecat' || true

echo "[3/5] d8"
java -cp "$R8_JAR" com.android.tools.r8.D8 --release --min-api 23 --lib "$ANDROID_JAR" \
    --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "[4/5] package"
cd "$OUT" && cp base.apk unsigned.apk && cd dex && zip -q -X ../unsigned.apk classes.dex && cd ..
zipalign -p -f 4 unsigned.apk aligned.apk

echo "[5/5] sign"
[ -f "$KEYSTORE" ] || keytool -genkeypair -keystore "$KEYSTORE" -storepass "$STOREPASS" \
    -keypass "$STOREPASS" -alias publish -keyalg RSA -keysize 4096 -validity 10950 \
    -dname "CN=Roblox Publish, O=Boddy, C=US" >/dev/null 2>&1
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$STOREPASS" --key-pass "pass:$STOREPASS" \
    --ks-key-alias publish --v1-signing-enabled true --v2-signing-enabled true \
    --v3-signing-enabled true --out "$OUT/RobloxPublish.apk" aligned.apk
apksigner verify --min-sdk-version 23 "$OUT/RobloxPublish.apk"
echo "built: $OUT/RobloxPublish.apk"
