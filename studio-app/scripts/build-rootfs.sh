#!/bin/bash
# Builds the prebuilt Boddy system inside an arm64 debian:trixie container (run by CI under
# qemu). The phone then only downloads and unpacks it instead of installing ~150 packages.
# Expects this repository mounted at /src.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
A=/src/app/src/main/assets/boddy

install -m644 "$A/apt-boddy.conf" /etc/apt/apt.conf.d/99boddy
mkdir -p /etc/dpkg/dpkg.cfg.d && install -m644 "$A/dpkg-boddy.cfg" /etc/dpkg/dpkg.cfg.d/99boddy

apt-get update
# shellcheck disable=SC2046
apt-get install -y --no-install-recommends $(grep -v '^\s*#' "$A/packages.txt")

# AI tools. Failures here aren't fatal: setup.sh on the phone retries them.
curl -fsSL https://claude.ai/install.sh | bash || echo "Claude Code install failed"
npm install -g @openai/codex || echo "Codex install failed"

# Keep apt's package lists (so installing more works right away), drop downloaded archives.
apt-get clean
rm -rf /tmp/* /root/.npm /root/.cache
