#!/bin/bash
# Claude Code running on DeepSeek's models, via DeepSeek's Anthropic-compatible API.
# Uses its own config folder so it never mixes with your Claude sign-in.
# Run with --reset to enter a different API key.
export PATH="/opt/boddy/phone-bin:/root/.local/bin:$PATH"
export BROWSER=/opt/boddy/open-on-phone
KEYFILE=/root/.config/boddy/deepseek-key
mkdir -p /root/.config/boddy /root/Projects && cd /root/Projects
[ "$1" = "--reset" ] && rm -f "$KEYFILE"

if ! command -v claude >/dev/null; then
    echo "Installing Claude Code..."
    curl -fsSL https://claude.ai/install.sh | bash
fi

if [ ! -s "$KEYFILE" ]; then
    clear
    cat <<'MSG'
  DeepSeek Code needs a DeepSeek API key (one time only).

  1. Your phone's browser is opening platform.deepseek.com
  2. Sign in, create an API key, and copy it
  3. Come back here, tap the Paste key on the bottom bar, then Enter

MSG
    /opt/boddy/open-on-phone https://platform.deepseek.com/api_keys
    read -rsp "  DeepSeek API key: " key; echo
    key=$(printf '%s' "$key" | tr -d '[:space:]')
    [ -z "$key" ] && { echo "No key entered."; read -rp "Press Enter to close."; exit 1; }
    (umask 077; printf '%s' "$key" > "$KEYFILE")
fi

export CLAUDE_CONFIG_DIR=/root/.claude-deepseek
export ANTHROPIC_BASE_URL=https://api.deepseek.com/anthropic
export ANTHROPIC_AUTH_TOKEN="$(cat "$KEYFILE")"
export ANTHROPIC_MODEL=deepseek-v4-pro
export ANTHROPIC_DEFAULT_OPUS_MODEL=deepseek-v4-pro
export ANTHROPIC_DEFAULT_SONNET_MODEL=deepseek-v4-pro
export ANTHROPIC_DEFAULT_HAIKU_MODEL=deepseek-v4-flash
export CLAUDE_CODE_SUBAGENT_MODEL=deepseek-v4-flash
if ls /root/.claude-deepseek/projects/-root-Projects/*.jsonl >/dev/null 2>&1; then
    claude --continue
else
    claude
fi
echo
echo "To use a different DeepSeek key: /opt/boddy/deepseek-code.sh --reset"
read -rp "DeepSeek Code closed. Press Enter to close this window."
