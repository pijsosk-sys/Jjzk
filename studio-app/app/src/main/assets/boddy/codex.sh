#!/bin/bash
# Starts Codex. Sign-in ("Sign in with ChatGPT") opens in the phone's browser.
export PATH="/opt/boddy/phone-bin:/root/.local/bin:$PATH"
export BROWSER=/opt/boddy/open-on-phone
mkdir -p /root/Projects && cd /root/Projects
if ! command -v codex >/dev/null; then
    echo "Installing Codex..."
    npm install -g @openai/codex
fi
# The background app-server daemon needs a Unix socket that fails under proot.
codex --no-daemon
echo
read -rp "Codex closed. Press Enter to close this window."
