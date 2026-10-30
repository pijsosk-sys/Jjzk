#!/usr/bin/env bash
# Builds the Go relay into an Android library. Needs Go, the NDK and gomobile:
#   go get -tool golang.org/x/mobile/cmd/gobind && go install golang.org/x/mobile/cmd/gomobile@latest
set -euo pipefail

: "${ANDROID_HOME:?set ANDROID_HOME}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(ls -d "$ANDROID_HOME"/ndk/* | tail -1)}"
export PATH="$PATH:$(go env GOPATH)/bin"

# -checklinkname=0: github.com/wlynxg/anet (pulled in by the nethernet transport) reaches into
# net.zoneCache through a linkname the current Go linker rejects by default.
gomobile bind \
    -ldflags=-checklinkname=0 \
    -target=android/arm64 \
    -androidapi 24 \
    -o ../app/libs/bridge.aar \
    .

echo "built: ../app/libs/bridge.aar"
