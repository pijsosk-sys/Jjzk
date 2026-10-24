#!/bin/bash
# Starts the VNC display server and the Boddy desktop. Arguments: WIDTH HEIGHT
W=${1:-1600}
H=${2:-740}
export DISPLAY=:1 HOME=/root USER=root SHELL=/bin/bash
export PATH="/root/.local/bin:$PATH"
export XDG_RUNTIME_DIR=/tmp/runtime-root
export XDG_CURRENT_DESKTOP=XFCE
mkdir -p "$XDG_RUNTIME_DIR" /tmp/.X11-unix && chmod 700 "$XDG_RUNTIME_DIR" && chmod 1777 /tmp/.X11-unix
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1

# Re-apply launchers so newly installed apps (e.g. Antigravity) show up.
/opt/boddy/make-launchers.sh >/dev/null 2>&1

Xvnc :1 -geometry "${W}x${H}" -depth 24 -SecurityTypes None -localhost -nolisten tcp \
     -rfbport 5901 -AlwaysShared -AcceptSetDesktopSize=0 &
for _ in $(seq 50); do [ -e /tmp/.X11-unix/X1 ] && break; sleep 0.2; done

exec dbus-launch --exit-with-session startxfce4
