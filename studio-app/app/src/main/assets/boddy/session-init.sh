#!/bin/bash
# Runs inside each desktop session: wallpaper and tidy desktop icons.
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

