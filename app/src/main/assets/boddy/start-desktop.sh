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

# Sound: everything plays into a virtual speaker; the Boddy app streams it to the phone
# (raw 44.1 kHz 16-bit stereo on 127.0.0.1:4713, see AudioStream.kt).
pulseaudio --kill >/dev/null 2>&1
pulseaudio --daemonize=yes --exit-idle-time=-1 --disallow-exit=yes --disable-shm=yes -n \
    --load="module-null-sink sink_name=boddy sink_properties=device.description=Phone" \
    --load="module-native-protocol-unix socket=/tmp/pulse.sock auth-anonymous=1" \
    --load="module-simple-protocol-tcp source=boddy.monitor record=true playback=false listen=127.0.0.1 port=4713 rate=44100 format=s16le channels=2" \
    >/dev/null 2>&1 || echo "Sound unavailable (pulseaudio didn't start)"
export PULSE_SERVER=unix:/tmp/pulse.sock
mkdir -p /etc/pulse/client.conf.d
printf 'default-server = unix:/tmp/pulse.sock\nenable-shm = no\nautospawn = no\n' > /etc/pulse/client.conf.d/boddy.conf

Xvnc :1 -geometry "${W}x${H}" -depth 24 -SecurityTypes None -localhost -nolisten tcp \
     -rfbport 5901 -AlwaysShared -AcceptSetDesktopSize=0 &
for _ in $(seq 50); do [ -e /tmp/.X11-unix/X1 ] && break; sleep 0.2; done

# Desktop tools (boddy-screen etc.) need these even from separate terminals.
printf 'export DISPLAY=:1\n' > /etc/profile.d/boddy-display.sh

exec dbus-launch --exit-with-session startxfce4
