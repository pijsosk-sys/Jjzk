#!/bin/bash
# Creates the app icons used on the desktop, in the dock and in the menu.
APPS=/usr/share/applications
DESK=/root/Desktop
mkdir -p "$DESK" "$APPS" /opt/boddy/phone-bin

# Programs started with phone-bin first in PATH open links in the phone's browser.
ln -sf /opt/boddy/open-on-phone /opt/boddy/phone-bin/xdg-open
ln -sf /opt/boddy/open-on-phone /opt/boddy/phone-bin/sensible-browser

# Commands AI agents (and you) use to drive the desktop and the phone. See ai-setup.sh.
for c in boddy-screen boddy-click boddy-type boddy-key boddy-scroll boddy-run \
         phone-save phone-open phone-app phone-apps; do
    ln -sf /opt/boddy/boddy-ctl "/usr/local/bin/$c"
done

launcher() { # id name exec icon
    cat > "$APPS/boddy-$1.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=$2
Exec=$3
Icon=$4
Terminal=false
Categories=Boddy;
DESKTOP
}

launcher firefox "Firefox" "firefox-esr %u" firefox-esr
launcher claude "Claude" "firefox-esr https://claude.ai" firefox-esr
launcher claude-code "Claude Code" "xfce4-terminal --disable-server --title='Claude Code' -x /opt/boddy/claude-code.sh" utilities-terminal
launcher claude-signin "Sign in to Claude" "xfce4-terminal --disable-server --title='Sign in to Claude' -x /opt/boddy/claude-code.sh --signin" dialog-password
launcher codex "Codex" "xfce4-terminal --disable-server --title='Codex' -x /opt/boddy/codex.sh" utilities-terminal
launcher gemini "Gemini CLI" "xfce4-terminal --disable-server --title='Gemini CLI' -x /opt/boddy/free-ai.sh gemini" utilities-terminal
launcher qwen "Qwen Code" "xfce4-terminal --disable-server --title='Qwen Code' -x /opt/boddy/free-ai.sh qwen" utilities-terminal
launcher opencode "OpenCode" "xfce4-terminal --disable-server --title='OpenCode' -x /opt/boddy/free-ai.sh opencode" utilities-terminal
launcher deepseek "DeepSeek" "firefox-esr https://chat.deepseek.com" firefox-esr
launcher deepseek-code "DeepSeek Code" "xfce4-terminal --disable-server --title='DeepSeek Code' -x /opt/boddy/deepseek-code.sh" utilities-terminal
launcher apps "Install Apps" "synaptic" system-software-install
launcher files "Files" "thunar" system-file-manager
launcher terminal "Terminal" "xfce4-terminal" utilities-terminal

if [ -x /opt/antigravity/antigravity ]; then
    launcher antigravity "Antigravity" "/opt/antigravity/antigravity --no-sandbox --disable-gpu %F" \
        "$(find /opt/antigravity -name '*.png' -path '*linux*' | head -1)"
else
    launcher antigravity "Get Antigravity" "xfce4-terminal --disable-server --title='Install Antigravity' -x /opt/boddy/install-antigravity.sh" system-software-install
fi

# Desktop: the main apps, marked trusted so XFCE doesn't ask before launching them.
rm -f "$DESK"/boddy-*.desktop
for id in claude-code codex gemini qwen opencode deepseek-code claude deepseek antigravity firefox files; do
    cp "$APPS/boddy-$id.desktop" "$DESK/"
    chmod +x "$DESK/boddy-$id.desktop"
    gio set "$DESK/boddy-$id.desktop" metadata::xfce-exe-checksum \
        "$(sha256sum "$DESK/boddy-$id.desktop" | cut -d' ' -f1)" 2>/dev/null || true
done

# Dock launchers (see apply-theme.sh for the panel layout).
n=2
for id in firefox claude-code codex deepseek-code antigravity files terminal; do
    d=/root/.config/xfce4/panel/launcher-$n
    rm -rf "$d" && mkdir -p "$d"
    cp "$APPS/boddy-$id.desktop" "$d/boddy-$id.desktop"
    n=$((n + 1))
done
