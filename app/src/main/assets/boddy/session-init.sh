#!/bin/bash
# Runs inside each desktop session: wallpaper, tidy desktop icons, first-run sign-in.
for _ in $(seq 20); do pgrep -x xfdesktop >/dev/null && break; sleep 0.5; done
sleep 1

xfconf-query -c xfce4-desktop -l 2>/dev/null | grep 'last-image$' | while read -r prop; do
    xfconf-query -c xfce4-desktop -p "$prop" -s /usr/share/backgrounds/boddy/wallpaper.svg
    xfconf-query -c xfce4-desktop -p "${prop%last-image}image-style" -s 5 2>/dev/null || true
done
for k in show-filesystem show-home show-trash show-removable; do
    xfconf-query -c xfce4-desktop -p "/desktop-icons/file-icons/$k" -n -t bool -s false 2>/dev/null ||
        xfconf-query -c xfce4-desktop -p "/desktop-icons/file-icons/$k" -s false
done

# The first time, help the user sign in to Claude.
if [ ! -e /root/.config/boddy/signin-offered ]; then
    mkdir -p /root/.config/boddy && touch /root/.config/boddy/signin-offered
    xfce4-terminal --disable-server --title='Sign in to Claude' -x /opt/boddy/claude-code.sh --signin &
fi
