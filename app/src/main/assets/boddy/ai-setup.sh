#!/bin/bash
# Tells Claude Code, DeepSeek Code and Codex where they are and what they can do.
read -r -d '' GUIDE <<'MD'
# You are running inside Boddy

Boddy is a Linux desktop (Debian, XFCE) running inside an Android app on the user's phone
(Samsung Galaxy S24 Ultra). There is no PC. You run as root inside proot; there is no
systemd, no kernel modules and no GPU acceleration. Projects live in /root/Projects.

## Seeing and controlling the Boddy desktop
- `boddy-screen` saves a screenshot to /tmp/boddy-screen.png and prints the screen size.
  Read the PNG to see the screen. Take a new one after every action to check the result.
- `boddy-click X Y [right|double]`, `boddy-type "text"`, `boddy-key ctrl+s`,
  `boddy-scroll down 3`, `boddy-run firefox-esr https://example.com`

## The phone
- `phone-apps` lists every app on the phone; `phone-app NAME` opens one (e.g. `phone-app Minecraft`).
  Opening apps needs the Boddy desktop on screen. Opening an app sends Boddy to the background;
  the user comes back by switching to Boddy.
- `phone-save FILE` copies a file to the phone's Downloads/Boddy folder.
- `phone-open FILE` opens a file on the phone; `.mcaddon`, `.mcpack` and `.mcworld` files go
  straight into Minecraft Bedrock, which imports them.
- Other apps' private folders can't be written to from here: deliver files with phone-save
  and tell the user where to tap.

## Games the user makes things for
- Minecraft Bedrock (com.mojang.minecraftpe): make add-ons (behavior pack + resource pack,
  JSON and the @minecraft/server scripting API), zip them as a .mcaddon, then `phone-open` it.
- Minecraft Java via the DroidBridge Launcher app (owns Java Edition): make Fabric mods
  (Fabric Loom + Gradle; JDK 21 is installed, use Gradle toolchains if a newer one is needed).
  Build the jar, `phone-save` it, and tell the user to add it to the mods folder in DroidBridge.
- Roblox: Roblox Studio itself can't run here (Windows/Mac only), but Roblox files can be
  read and edited without it:
  - `roblox-extract game.rbxl` unpacks every script (Script = .server.luau, LocalScript =
    .client.luau, ModuleScript = .luau) into `game-scripts/`, plus `tree.txt` with the layout.
  - Edit the .luau files, then `roblox-apply game.rbxl game-scripts` writes game-edited.rbxl.
  - The phone's storage is at /root/Phone when the user allowed file access in the Boddy app
    (downloads are in /root/Phone/Download). If it's missing, ask them to tap "Allow phone files".
  - For new games: Rojo (`roblox-setup` installs it) builds a place from files
    (`rojo build -o game.rbxl`), and Roblox Open Cloud can publish it without Studio.
  - Save results to the phone with `phone-save`.
MD

mkdir -p /root/.claude /root/.claude-deepseek /root/.codex /root/.gemini /root/.qwen /root/.config/opencode
for f in /root/.claude/CLAUDE.md /root/.claude-deepseek/CLAUDE.md /root/.codex/AGENTS.md \
         /root/.gemini/GEMINI.md /root/.qwen/QWEN.md /root/.config/opencode/AGENTS.md; do
    printf '%s\n' "$GUIDE" > "$f"
done
