#!/usr/bin/env bash
# APK Pull — build without gradle. Needs ANDROID_HOME with build-tools;34.0.0 and platforms;android-34.
set -euo pipefail

SDK="${ANDROID_HOME:?set ANDROID_HOME to your sdk root}"
BT="$SDK/build-tools/${BUILD_TOOLS:-35.0.0}"
PLATFORM="$SDK/platforms/android-34/android.jar"
OUT="build"
KS="${KEYSTORE:-$OUT/debug.keystore}"

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/gen"

"$BT/aapt2" link \
    -I "$PLATFORM" \
    --manifest AndroidManifest.xml \
    --java "$OUT/gen" \
    --min-sdk-version 24 --target-sdk-version 34 \
    -o "$OUT/unsigned.apk"

find java -name '*.java' > "$OUT/srcs.txt"
javac -nowarn --release 11 -cp "$PLATFORM" -d "$OUT/classes" @"$OUT/srcs.txt"

find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --lib "$PLATFORM" --min-api 24 --output "$OUT" @"$OUT/classes.txt"

( cd "$OUT" && zip -q -0 unsigned.apk classes.dex )
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KS" ]; then
    keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
        -alias apkpull -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=apkpull" >/dev/null 2>&1
fi

"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/apkpull.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/apkpull.apk"

echo "built: $OUT/apkpull.apk  ($(du -h "$OUT/apkpull.apk" | cut -f1))"
