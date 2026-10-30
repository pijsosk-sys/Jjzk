#!/bin/bash
# Creates the icons used on the desktop, in the dock and in the menu. Studio Launcher keeps only
# what Roblox Studio needs: Studio itself, Firefox (for "Login via Browser"), files and a terminal.
APPS=/usr/share/applications
DESK=/root/Desktop
mkdir -p "$DESK" "$APPS" /opt/boddy/phone-bin

# Programs started with phone-bin first in PATH open links in the phone's browser.
ln -sf /opt/boddy/open-on-phone /opt/boddy/phone-bin/xdg-open
ln -sf /opt/boddy/open-on-phone /opt/boddy/phone-bin/sensible-browser
for c in boddy-screen boddy-click boddy-type boddy-key boddy-scroll boddy-run phone-save phone-open; do
    ln -sf /opt/boddy/boddy-ctl "/usr/local/bin/$c"
done
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

launcher studio-open "Open Roblox Studio" "/opt/boddy/studio-app.sh \"Open Studio\"" applications-development
launcher studio "Roblox Studio menu" "/opt/boddy/studio-app.sh" applications-development
launcher firefox "Firefox" "firefox-esr %u" firefox-esr
launcher files "Files" "thunar" system-file-manager
launcher terminal "Terminal" "xfce4-terminal" utilities-terminal

# Desktop: marked trusted so XFCE doesn't ask before launching them.
rm -f "$DESK"/boddy-*.desktop
for id in studio-open studio firefox files terminal; do
    cp "$APPS/boddy-$id.desktop" "$DESK/"
    chmod +x "$DESK/boddy-$id.desktop"
    gio set "$DESK/boddy-$id.desktop" metadata::xfce-exe-checksum \
        "$(sha256sum "$DESK/boddy-$id.desktop" | cut -d' ' -f1)" 2>/dev/null || true
done
# Leftovers from a full Boddy image: their apps aren't part of Studio Launcher.
for f in "$APPS"/boddy-*.desktop; do
    case "$(basename "$f" .desktop)" in
        boddy-studio-open|boddy-studio|boddy-firefox|boddy-files|boddy-terminal) ;;
        *) rm -f "$f" ;;
    esac
done

# Dock launchers (see apply-theme.sh for the panel layout).
n=2
for id in studio-open firefox files terminal; do
    d=/root/.config/xfce4/panel/launcher-$n
    rm -rf "$d" && mkdir -p "$d"
    cp "$APPS/boddy-$id.desktop" "$d/boddy-$id.desktop"
    n=$((n + 1))
done
for n in 6 7 8; do rm -rf /root/.config/xfce4/panel/launcher-$n; done
