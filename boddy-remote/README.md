# Claude Remote for Boddy

Chat with Claude in the **Claude app** (phone or PC) while Claude Code does the work on the
**Boddy desktop**. It runs `claude remote-control` inside Boddy, so the Boddy session shows up
in the Claude app under **Code**.

## Install

**Without reinstalling Boddy (keeps all your stuff):** put `install-claude-remote.sh` in the
phone's Downloads folder, then in the Boddy terminal run:

```
bash ~/Phone/Download/install-claude-remote.sh
```

(`~/Phone` needs "Allow phone files" in the Boddy app.) This survives desktop restarts.

**Or the APK** `Boddy-Studio-Compat-v13-ClaudeRemote.apk` (built with `build_apk.py`) has it built in.
It is signed with a different key than v12, so Android makes you uninstall v12 first, and that
deletes Boddy's data.

## Use

1. Sign in once with the **Claude Code** icon (Claude account with a subscription).
2. Tap the **Claude Remote** icon on the desktop. The first time, answer any question it asks.
3. In the Claude app, open **Code** and pick the Boddy session. Chat there.

You can close the Claude Remote window. It keeps running and comes back after Boddy restarts.
`boddy-remote stop` turns it off, and `boddy-remote status` shows its state and log.

## Keeping it from crashing

- **It restarts itself.** If `claude remote-control` exits, it starts again within 5 to 60 s.
  If the tmux session is killed, a watchdog starts it again within 20 s. If Android kills the
  whole app, it starts on the next desktop start.
- **A safety guard blocks commands that would crash Boddy.** It runs as a Claude Code hook
  (`boddy-guard.py`) and covers rebooting, shutting down, logging out, killing every process,
  killing the desktop or Claude itself, wiping system folders, `mkfs`/`dd` to disks, fork bombs
  and removing core packages. Claude is told to restart only the stuck program instead.
- `reboot`, `shutdown`, `poweroff`, `halt` and `telinit` are replaced with harmless stand-ins.
- Claude's guide (`~/.claude/CLAUDE.md`) explains this and asks Claude to keep memory use low.

Android can still stop Boddy when the phone runs out of memory, or when the app has more than
32 processes (Android 12+). See the main README's "Boddy keeps reconnecting" section for how to
turn that limit off.

## Files

| File | What |
|---|---|
| `boddy-remote` | start / stop / status / watchdog |
| `remote-control.sh` | keeps `claude remote-control` running |
| `boddy-guard.py` | the safety hook |
| `remote-setup.sh` | installs everything (run at every desktop start by the APK) |
| `make_installer.sh` | builds `install-claude-remote.sh` |
| `build_apk.py` | adds all this to a Boddy APK |
