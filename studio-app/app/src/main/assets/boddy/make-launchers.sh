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
         phone-save phone-open phone-app phone-apps phone-status phone-screen phone-ui \
         phone-tap phone-hold phone-swipe phone-do phone-key phone-type phone-split phone-spot phone-spots; do
    ln -sf /opt/boddy/boddy-ctl "/usr/local/bin/$c"
done
for c in roblox-extract roblox-apply roblox-setup; do
    ln -sf /opt/boddy/roblox-tool "/usr/local/bin/$c"
done
ln -sf /opt/boddy/mcbot /usr/local/bin/mcbot
ln -sf /opt/boddy/studio /usr/local/bin/roblox-studio

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
launcher mcbot "Minecraft Bot" "/opt/boddy/mcbot-app.sh" applications-games
launcher studio "Roblox Studio (experimental)" "/opt/boddy/studio-app.sh" applications-development
launcher phone-apps "Phone Apps" "/opt/boddy/phone-apps-app.sh" phone
launcher roblox-tools "Roblox Tools" "/opt/boddy/roblox-tools-app.sh" applications-games
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
# Shortcuts to the game apps found on the phone (from phone-apps.txt, written by the app).
rm -f "$APPS"/boddy-phone-*.desktop
phone_ids=""
while IFS=: read -r title state; do
    case "$state" in
        *installed\ \(*)
            id="phone-$(echo "$title" | tr 'A-Z ' 'a-z-')"
            launcher "$id" "$title" "/opt/boddy/boddy-ctl phone-app $title" applications-games
            phone_ids="$phone_ids $id"
            ;;
    esac
done < /opt/boddy/phone-apps.txt 2>/dev/null

for id in claude-code codex gemini qwen opencode deepseek-code roblox-tools studio mcbot phone-apps $phone_ids claude deepseek antigravity firefox files; do
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
