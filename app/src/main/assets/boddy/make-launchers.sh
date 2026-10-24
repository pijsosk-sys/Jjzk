#!/bin/bash
# Creates the desktop icons and the panel launchers.
DESK=/root/Desktop
mkdir -p "$DESK" /usr/share/applications

launcher() { # file name exec icon terminal
    cat > "/usr/share/applications/$1.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=$2
Exec=$3
Icon=$4
Terminal=$5
Categories=Boddy;
DESKTOP
    cp "/usr/share/applications/$1.desktop" "$DESK/$1.desktop"
    chmod +x "$DESK/$1.desktop"
}

launcher boddy-firefox "Firefox" "firefox-esr %u" firefox-esr false
launcher boddy-claude "Claude" "firefox-esr https://claude.ai" firefox-esr false
launcher boddy-claude-code "Claude Code" "xfce4-terminal --title='Claude Code' -x bash -lc 'command -v claude >/dev/null || curl -fsSL https://claude.ai/install.sh | bash; claude; exec bash'" utilities-terminal false
launcher boddy-apps "Install Apps" "synaptic" system-software-install false
launcher boddy-files "Files" "thunar" system-file-manager false
launcher boddy-terminal "Terminal" "xfce4-terminal" utilities-terminal false

if [ -x /opt/antigravity/antigravity ]; then
    launcher boddy-antigravity "Antigravity" "/opt/antigravity/antigravity --no-sandbox --disable-gpu %F" /opt/antigravity/resources/app/resources/linux/code.png false
else
    launcher boddy-antigravity "Get Antigravity" "xfce4-terminal --title='Install Antigravity' -x /opt/boddy/install-antigravity.sh" system-software-install false
fi

# Desktop icons are "trusted" only once XFCE has seen their checksum; mark them.
for f in "$DESK"/*.desktop; do
    gio set "$f" metadata::xfce-exe-checksum "$(sha256sum "$f" | cut -d' ' -f1)" 2>/dev/null || true
done
