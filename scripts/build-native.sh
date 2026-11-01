#!/usr/bin/env bash
# Builds Horizon for Android as libhorizon.so, into the app's jniLibs.
#
#   scripts/build-native.sh [--release|--debug] [ABI...]   (default: --release arm64-v8a)
#
# Steps: check out Horizon at HORIZON_COMMIT into build/horizon, apply
# patches/horizon-android.patch, copy native/ in as crates/horizon-android and
# build it with cargo-ndk.
#
# Needs: git, a Rust toolchain (1.90+) with the Android targets
# (rustup target add aarch64-linux-android x86_64-linux-android), cargo-ndk
# (cargo install cargo-ndk) and the Android NDK (ANDROID_NDK_HOME, or
# ANDROID_HOME/ndk/<version>).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
commit="$(tr -d '[:space:]' < "$root/HORIZON_COMMIT")"
horizon="$root/build/horizon"
profile=release
abis=()
for arg in "$@"; do
  case "$arg" in
    --release) profile=release ;;
    --debug) profile=dev ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    *) abis+=("$arg") ;;
  esac
done
[ ${#abis[@]} -gt 0 ] || abis=(arm64-v8a)

if [ -z "${ANDROID_NDK_HOME:-}" ] && [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/ndk" ]; then
  ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$(ls "$ANDROID_HOME/ndk" | sort -V | tail -1)"
  export ANDROID_NDK_HOME
fi
: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to the Android NDK}"

# 1. Horizon at the pinned commit, with only our patch on it.
if [ ! -d "$horizon/.git" ]; then
  mkdir -p "$root/build"
  git init -q "$horizon"
  git -C "$horizon" remote add origin https://github.com/horizon-rust/horizon
fi
if [ "$(git -C "$horizon" rev-parse -q --verify HEAD 2>/dev/null)" != "$commit" ]; then
  git -C "$horizon" fetch -q --depth 1 origin "$commit"
  git -C "$horizon" checkout -q --force --detach "$commit"
fi
git -C "$horizon" reset -q --hard "$commit"
git -C "$horizon" clean -q -fd -e /target/
git -C "$horizon" apply "$root/patches/horizon-android.patch"

# 2. The Android crate, next to horizon-client.
rm -rf "$horizon/crates/horizon-android"
cp -R "$root/native" "$horizon/crates/horizon-android"

# 3. Build. API 26: AAudio, which the sound output uses.
out="$root/android/app/src/main/jniLibs"
mkdir -p "$out"
targets=()
for abi in "${abis[@]}"; do targets+=(-t "$abi"); done
cd "$horizon"
if [ "$profile" = release ]; then
  cargo ndk "${targets[@]}" -P 26 -o "$out" build -p horizon-android --release
else
  cargo ndk "${targets[@]}" -P 26 -o "$out" build -p horizon-android
fi
echo "Built: $(find "$out" -name libhorizon.so | tr '\n' ' ')"
