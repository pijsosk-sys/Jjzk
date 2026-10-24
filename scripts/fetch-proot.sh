#!/usr/bin/env bash
# Downloads the aarch64 proot build from the Termux package repo and places it in
# app/src/main/jniLibs/arm64-v8a so Android extracts it to the app's nativeLibraryDir
# (the only place a modern Android app is allowed to execute binaries from).
set -euo pipefail

REPO="https://packages.termux.dev/apt/termux-main"
OUT="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/jniLibs/arm64-v8a"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

curl -fsSL "$REPO/dists/stable/main/binary-aarch64/Packages" -o "$WORK/Packages"

fetch() {
  local pkg="$1"
  local file
  file=$(awk -v p="$pkg" '$1=="Package:"{cur=$2} cur==p && $1=="Filename:"{print $2; exit}' "$WORK/Packages")
  [ -n "$file" ] || { echo "package $pkg not found" >&2; exit 1; }
  echo "Fetching $file"
  curl -fsSL "$REPO/$file" -o "$WORK/$pkg.deb"
  dpkg-deb -x "$WORK/$pkg.deb" "$WORK/$pkg"
}

fetch proot
fetch libtalloc

PREFIX="data/data/com.termux/files/usr"
mkdir -p "$OUT"
cp -L "$WORK/proot/$PREFIX/bin/proot" "$OUT/libproot.so"
cp -L "$WORK/proot/$PREFIX/libexec/proot/loader" "$OUT/libproot-loader.so"
cp -L "$WORK/libtalloc/$PREFIX/lib/libtalloc.so.2" "$OUT/libtalloc.so"

# Android only packages files named lib*.so, so point proot at the renamed talloc
# and drop the Termux-specific library search path.
patchelf --replace-needed libtalloc.so.2 libtalloc.so "$OUT/libproot.so"
patchelf --remove-rpath "$OUT/libproot.so"
chmod 755 "$OUT"/*.so
ls -l "$OUT"
