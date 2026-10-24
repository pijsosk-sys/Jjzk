#!/bin/bash
# Starts a free AI coding agent, downloading it the first time. Argument: gemini | qwen | opencode
# Sign-in links open in the phone's browser. Follows Boddy's "AI mode" setting.
export PATH="/opt/boddy/phone-bin:/root/.local/bin:$PATH"
export BROWSER=/opt/boddy/open-on-phone
mkdir -p /root/Projects && cd /root/Projects

case "$1" in
    gemini) name="Gemini CLI"; cmd=gemini; pkg=@google/gemini-cli ;;
    qwen) name="Qwen Code"; cmd=qwen; pkg=@qwen-code/qwen-code ;;
    opencode) name="OpenCode"; cmd=opencode; pkg=opencode-ai ;;
    *) echo "usage: free-ai.sh gemini|qwen|opencode"; exit 2 ;;
esac

if ! command -v "$cmd" >/dev/null; then
    echo "Downloading $name (first time only)..."
    npm install -g "$pkg" || { read -rp "Download failed. Press Enter to close."; exit 1; }
fi

auto=false
[ "$(cat /root/.config/boddy/agent-mode 2>/dev/null)" = auto ] && auto=true
args=()
if [ "$cmd" = opencode ]; then
    # OpenCode takes its permissions from its config file rather than a flag.
    mode=ask; $auto && mode=allow
    mkdir -p /root/.config/opencode
    printf '{\n  "$schema": "https://opencode.ai/config.json",\n  "permission": { "edit": "%s", "bash": "%s", "webfetch": "%s" }\n}\n' \
        "$mode" "$mode" "$mode" > /root/.config/opencode/opencode.json
else
    $auto && args+=(--yolo)
fi
"$cmd" "${args[@]}"
echo
read -rp "$name closed. Press Enter to close this window."
