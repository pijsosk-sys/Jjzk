#!/bin/bash
# Installs Claude Remote and Boddy's crash guards. Safe to run any number of times; the
# Boddy desktop runs it at every start so nothing is lost when the app refreshes /opt/boddy.
# By hand (no app update needed):  bash remote-setup.sh
SRC=$(cd "$(dirname "$(readlink -f "$0")")" && pwd)
DIR=/root/.boddy/remote
mkdir -p "$DIR" /root/.claude /root/.config/boddy /root/.config/autostart /root/Desktop \
         /usr/share/applications /usr/local/sbin /usr/local/bin

# 1. Our files live in /root, so they survive app updates and desktop restarts.
if [ "$SRC" != "$DIR" ]; then
    for f in boddy-remote remote-control.sh boddy-guard.py remote-setup.sh; do
        [ -f "$SRC/$f" ] && install -m755 "$SRC/$f" "$DIR/$f"
    done
fi
ln -sf "$DIR/boddy-remote" /usr/local/bin/boddy-remote

# 2. Rebooting or powering off can't restart anything inside an Android app; it can only
#    take the desktop down. These stand-ins say so and leave everything running.
for c in reboot shutdown poweroff halt telinit; do
    cat > "/usr/local/sbin/$c" <<SHIM
#!/bin/sh
echo "$c: not available in Boddy (it would crash the desktop). Restart Boddy from the phone app if needed." >&2
exit 0
SHIM
    chmod 755 "/usr/local/sbin/$c"
done

# 3. Claude Code: the guard hook plus deny rules, merged into existing settings.
python3 - "$DIR/boddy-guard.py" <<'PY' || echo "Could not update Claude settings" >&2
import json, os, sys
path = "/root/.claude/settings.json"
try:
    with open(path) as f:
        s = json.load(f)
    if not isinstance(s, dict):
        raise ValueError
except FileNotFoundError:
    s = {}
except Exception:
    os.replace(path, path + ".broken")  # keep the user's file, start clean
    s = {}
cmd = "python3 " + sys.argv[1]
pre = s.setdefault("hooks", {}).setdefault("PreToolUse", [])
pre[:] = [e for e in pre if not any("boddy-guard.py" in (h.get("command") or "") for h in e.get("hooks", []))]
pre.append({"matcher": "Bash", "hooks": [{"type": "command", "command": cmd, "timeout": 10}]})
deny = s.setdefault("permissions", {}).setdefault("deny", [])
for rule in ["Bash(reboot:*)", "Bash(shutdown:*)", "Bash(poweroff:*)", "Bash(halt:*)", "Bash(telinit:*)",
             "Bash(killall5:*)", "Bash(kill -9 -1:*)", "Bash(kill -1:*)", "Bash(xfce4-session-logout:*)",
             "Bash(rm -rf /)", "Bash(rm -rf /*)", "Bash(rm -rf ~)"]:
    if rule not in deny:
        deny.append(rule)
tmp = path + ".tmp"
with open(tmp, "w") as f:
    json.dump(s, f, indent=2)
os.replace(tmp, path)
PY

# 4. Tell Claude about Remote and the rules (between markers, so re-runs replace it).
python3 - <<'PY' || true
import os, re
guide = """<!-- boddy-remote:start -->
## Claude Remote (the user may be chatting from the Claude app)
You may be running under `claude remote-control`: the user chats with you from the Claude app
on their phone or a PC and may not be looking at the Boddy desktop. So:
- Do the work yourself on the desktop with `boddy-screen` (then Read the PNG), `boddy-click`,
  `boddy-type`, `boddy-key`, `boddy-scroll` and `boddy-run`. Check with a new screenshot after
  each step instead of assuming it worked.
- Tell the user in the chat what you did and what you see. To show them a picture or a file,
  `phone-save` it (it lands in the phone's Downloads/Boddy folder) and say so.
- Keep going until the task is done; ask only when you truly need a decision from them.

## Never crash Boddy
Boddy is one process tree inside an Android app. Nothing here can restart it, so never reboot,
shut down, log out, kill every process, kill the desktop's programs (Xvnc, xfce4-*, dbus,
pulseaudio, proot, tmux, claude), or delete system folders. A guard blocks these commands.
To "reset" a stuck program, close its window or kill only that program by its own PID; to reset
settings, change or move the one config file involved (keep a backup). If the whole desktop
really needs a restart, ask the user to do it from the Boddy app.
Memory is limited: close programs you opened and no longer need, and don't start more than a
few heavy ones at once (Android kills Boddy when it runs out of memory or has too many processes).
<!-- boddy-remote:end -->"""
path = "/root/.claude/CLAUDE.md"
try:
    text = open(path).read()
except FileNotFoundError:
    text = ""
text = re.sub(r"\n*<!-- boddy-remote:start -->.*?<!-- boddy-remote:end -->\n*", "\n", text, flags=re.S).rstrip("\n")
with open(path, "w") as f:
    f.write((text + "\n\n" if text else "") + guide + "\n")
PY

# 5. The Claude Remote icon, and a start-up entry that brings Remote back after a restart.
icon() { # path
    cat > "$1" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Claude Remote
Comment=Chat in the Claude app while Claude uses this desktop
Exec=xfce4-terminal --disable-server --title='Claude Remote' -x $DIR/boddy-remote open
Icon=network-wireless
Terminal=false
Categories=Boddy;
DESKTOP
}
icon /usr/share/applications/claude-remote.desktop
icon /root/Desktop/claude-remote.desktop
chmod +x /root/Desktop/claude-remote.desktop
gio set /root/Desktop/claude-remote.desktop metadata::xfce-exe-checksum \
    "$(sha256sum /root/Desktop/claude-remote.desktop | cut -d' ' -f1)" 2>/dev/null || true

cat > /root/.config/autostart/claude-remote.desktop <<DESKTOP
[Desktop Entry]
Type=Application
Name=Claude Remote (auto start)
Exec=bash -c 'bash $DIR/remote-setup.sh >/dev/null 2>&1; bash $DIR/boddy-remote autostart'
Terminal=false
X-GNOME-Autostart-enabled=true
DESKTOP
echo "Claude Remote installed. Open the Claude Remote icon on the desktop to turn it on."
