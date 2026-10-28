#!/usr/bin/env python3
"""Claude Code PreToolUse hook: blocks shell commands that would crash or wipe the Boddy desktop.

Boddy is one proot process tree inside an Android app. Rebooting, killing every process, killing the
desktop's own programs or deleting system folders doesn't restart anything; it just takes the desktop
(and Claude with it) down. Exit code 2 blocks the command and tells Claude why.
"""
import json, re, sys

# The desktop and the things that keep Claude Remote alive.
CORE = r"(Xvnc|Xtigervnc|vncserver|xfce4-session|xfwm4|xfdesktop|xfce4-panel|xfconfd|dbus-daemon|dbus-launch|" \
       r"startxfce4|pulseaudio|proot|tmux|claude|boddy-remote|remote-control\.sh|bash|sh|init)"
END = r"(?=[\s'\";&|)]|$)"  # end of a word, so "sh" doesn't match "build.sh"

RULES = [
    (r"(^|[;&|(`]\s*|\bsudo\s+|\bexec\s+)(reboot|shutdown|poweroff|halt|telinit|kexec)\b", "rebooting or shutting down"),
    (r"(^|[;&|(`]\s*|\bsudo\s+)init\s+[0-6sS]\b", "changing the runlevel"),
    (r"\bsystemctl\s+(reboot|poweroff|halt|kexec|suspend|hibernate|isolate|emergency|rescue|default)\b", "rebooting via systemctl"),
    (r"\bkill\s+(-\S+\s+)*-1\b", "killing every process"),
    (r"\bkill\s+(-\S+\s+)*(1|0)(\s|$|;|&|\|)", "killing PID 1 or the whole process group"),
    (r"\bkillall5\b", "killing every process"),
    (r"\bpkill\s+(-\S+\s+)*-u\s*(root|0)\b", "killing every root process (that is everything here)"),
    (r"\bkillall\s+(-\S+\s+)*-u\s*(root|0)\b", "killing every root process (that is everything here)"),
    (r"\b(pkill|killall)\s+(-\S+\s+)*['\"]?" + CORE + END, "killing the Boddy desktop or Claude itself"),
    (r"\bkill\b[^;&|]*\$\(\s*(pgrep|pidof)\s+(-\S+\s+)*['\"]?" + CORE + END, "killing the Boddy desktop or Claude itself"),
    (r"\bxfce4-session-logout\b", "logging out of the desktop"),
    (r"\bvncserver\s+-kill\b", "stopping the desktop's display"),
    (r"\bxrandr\b[^;&|]*--off\b", "turning the screen off"),
    (r"\bxkill\b", "xkill can close the desktop by accident"),
    (r":\s*\(\s*\)\s*\{.*:\s*\|\s*:", "a fork bomb"),
    (r"\bmkfs(\.\w+)?\b|\bdd\b[^;&|]*\bof=/dev/(?!null\b|zero\b|stdout\b|stderr\b)", "writing to a disk device"),
    (r"\b(chmod|chown)\s+(-\S+\s+)*-R\b[^;&|]*\s/(\s|$|\*)", "changing permissions of the whole system"),
    (r"\brm\s+[^;&|]*\s['\"]?(/|~|/root|/root/Projects|/root/\.boddy|/root/\.claude|/root/\.local|/root/\.local/bin|/opt|/opt/boddy|"
     r"/usr|/usr/bin|/usr/lib|/usr/local|/usr/local/bin|/usr/share|/etc|/bin|/sbin|/lib|/lib64|/var|/var/lib|/home|/tmp/\.X11-unix)"
     r"/?\*?['\"]?(?=[\s;&|)]|$)", "deleting a system folder or Boddy itself"),
    (r"\bapt(-get)?\s+(-\S+\s+)*(remove|purge|autoremove)\b[^;&|]*\b(xfce4\S*|tigervnc\S*|dbus\S*|bash|coreutils|python3|tmux|nodejs|libc6|apt|dpkg|sudo)\b",
     "uninstalling a package the desktop needs"),
]

def check(cmd):
    for pattern, why in RULES:
        if re.search(pattern, cmd, re.IGNORECASE | re.MULTILINE):
            return why
    return None

def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        return 0  # never break Claude because of the guard itself
    if data.get("tool_name") != "Bash":
        return 0
    cmd = (data.get("tool_input") or {}).get("command") or ""
    why = check(cmd)
    if not why:
        return 0
    print("Blocked by Boddy's safety guard: " + why + ". Boddy runs inside an Android app, so this would "
          "crash the desktop instead of restarting it. To fix a stuck program, close or kill only that "
          "program (by its own PID). If the whole desktop really needs a restart, ask the user to do it "
          "from the Boddy app.", file=sys.stderr)
    return 2

if __name__ == "__main__":
    try:
        sys.exit(main())
    except SystemExit:
        raise
    except Exception:
        sys.exit(0)
