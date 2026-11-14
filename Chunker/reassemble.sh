#!/usr/bin/env bash
# Reassemble Chunker parts back into the original file.
# Usage: ./reassemble.sh <original_name>   (run in the folder holding the parts)
#   e.g. ./reassemble.sh cn.classfun.droidvm_0.0.6_300.apk
set -euo pipefail
name="${1:?usage: reassemble.sh <original_name>}"
cat "$name".gz.part* > "$name".gz
gunzip -f "$name".gz
echo "rebuilt: $name"
if [ -f "$name.manifest.txt" ]; then
  want=$(grep '^sha256=' "$name.manifest.txt" | cut -d= -f2)
  got=$(sha256sum "$name" | cut -d' ' -f1)
  [ "$want" = "$got" ] && echo "sha256 OK" || echo "sha256 MISMATCH want=$want got=$got"
fi
