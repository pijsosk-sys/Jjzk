#!/bin/bash
# Starts Codex. Sign-in ("Sign in with ChatGPT") opens in the phone's browser.
export PATH="/opt/boddy/phone-bin:/root/.local/bin:$PATH"
export BROWSER=/opt/boddy/open-on-phone
mkdir -p /root/Projects && cd /root/Projects
# While Roblox Studio runs, Android's limit of 32 processes is nearly reached; Codex (Node, and npm the first
# time) would add several. Only start it then when asked to (run-in-desktop.sh has asked already).
running=/root/.config/boddy/studio-running
if [ -z "${BODDY_CODEX_CONFIRMED:-}" ] && [ -e "$running" ] && kill -0 "$(cat "$running" 2>/dev/null)" 2>/dev/null; then
    echo "Roblox Studio is running: $(ls -d /proc/[0-9]* 2>/dev/null | wc -l) of 32 processes are in use."
    echo "Codex adds several more, and Android may then close Boddy."
    read -rp "Start Codex anyway? [y/N] " answer
    case "$answer" in [yY]*) ;; *) exit 0 ;; esac
fi
if ! command -v codex >/dev/null; then
    echo "Installing Codex..."
    npm install -g @openai/codex
fi
# The background app-server daemon needs a Unix socket that fails under proot.
codex --no-daemon
echo
read -rp "Codex closed. Press Enter to close this window."
