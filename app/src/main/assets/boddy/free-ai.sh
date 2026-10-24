#!/bin/bash
# Starts a free AI coding agent, downloading it the first time. Argument: gemini | qwen
# Sign-in links open in the phone's browser. Follows Boddy's "AI mode" setting.
export PATH="/opt/boddy/phone-bin:/root/.local/bin:$PATH"
export BROWSER=/opt/boddy/open-on-phone
mkdir -p /root/Projects && cd /root/Projects

case "$1" in
    gemini) name="Gemini CLI"; cmd=gemini; pkg=@google/gemini-cli ;;
    qwen) name="Qwen Code"; cmd=qwen; pkg=@qwen-code/qwen-code ;;
    *) echo "usage: free-ai.sh gemini|qwen"; exit 2 ;;
esac

if ! command -v "$cmd" >/dev/null; then
    echo "Downloading $name (first time only)..."
    npm install -g "$pkg" || { read -rp "Download failed. Press Enter to close."; exit 1; }
fi

args=()
[ "$(cat /root/.config/boddy/agent-mode 2>/dev/null)" = auto ] && args+=(--yolo)
"$cmd" "${args[@]}"
echo
read -rp "$name closed. Press Enter to close this window."
