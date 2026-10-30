#!/bin/bash
# Starts Claude Code. Sign-in links open in the phone's browser, where you're
# probably already logged in to claude.ai.
export PATH="/opt/boddy/phone-bin:/root/.local/bin:$PATH"
export BROWSER=/opt/boddy/open-on-phone
mkdir -p /root/Projects && cd /root/Projects

if ! command -v claude >/dev/null; then
    echo "Installing Claude Code..."
    curl -fsSL https://claude.ai/install.sh | bash
fi

if [ "$1" = "--signin" ]; then
    mkdir -p /root/.config/boddy && touch /root/.config/boddy/signin-offered
    clear
    cat <<'MSG'
  ┌──────────────────────────────────────────────────────────┐
  │  Sign in to Claude                                       │
  │                                                          │
  │  1. Pick a theme, then "Claude account with subscription" │
  │  2. Your phone's browser opens: tap Authorize            │
  │  3. Come back here. That's it!                           │
  │                                                          │
  │  If it asks for a code, copy it, come back, and tap the  │
  │  Paste key on the bottom bar.                            │
  └──────────────────────────────────────────────────────────┘

MSG
fi
# Continue the last conversation if there is one, so a restart doesn't lose your work.
if [ "$1" != "--signin" ] && ls /root/.claude/projects/-root-Projects/*.jsonl >/dev/null 2>&1; then
    claude --continue
else
    claude
fi
echo
read -rp "Claude Code closed. Press Enter to close this window."
