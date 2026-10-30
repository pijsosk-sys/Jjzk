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
  Opening apps needs the Boddy desktop on screen. Opening an app sends Boddy to the background
  (Boddy and you keep running); `phone-split NAME` opens it next to Boddy in split screen instead.
- `phone-save FILE` copies a file to the phone's Downloads/Boddy folder.
- `phone-open FILE` opens a file on the phone; `.mcaddon`, `.mcpack` and `.mcworld` files go
  straight into Minecraft Bedrock, which imports them.
- Other apps' private folders can't be written to from here: deliver files with phone-save
  and tell the user where to tap.

## Controlling phone apps (seeing the screen, tapping, playing games)
This works whenever the user turned on "Let AI control phone apps" in the Boddy app, even when
Boddy is in the background. `phone-status` says if it's on and which app is on screen.
- `phone-screen` saves /tmp/phone-screen.jpg and prints its size; Read it to see the phone.
  `phone-screen grid` adds lines every 100 px with labels, handy for exact coordinates.
  All X Y below are pixels of that picture.
- `phone-ui` lists buttons and text with their X Y. Fast and exact for normal apps and menus;
  games draw their own screen, so use `phone-screen` there.
- `phone-tap X Y`, `phone-hold X Y MS`, `phone-swipe X1 Y1 X2 Y2 [MS]`, `phone-type "text"`,
  `phone-key back|home|recents|notifications|split`.
- `phone-do "SCRIPT"` runs a burst of moves in one go. Use it: every command you run costs
  seconds of thinking, so plan several moves at once. Steps are separated by `;` and run in
  order, `wait MS` pauses, and `+` joins fingers that move at the same time (like two thumbs).
  Each finger is `tap X Y`, `hold X Y MS` or `swipe X1 Y1 X2 Y2 MS`, and may end with `@MS` to
  start later. Example (walk forward 2 s while turning, then jump):
  `phone-do "hold 200 560 2000 + swipe 1200 360 1350 360 800@300; tap 1460 630"`
- Button memory: `phone-spot APP NAME X Y` saves a spot, and `APP.NAME` then works anywhere X Y
  does (`phone-tap minecraft.jump`, `phone-do "hold minecraft.forward 1500"`). Before playing,
  run `phone-spots APP`. If it's empty or a spot looks wrong, take a grid screenshot, find the
  controls once and save them all. Keep other things you learn (how far a swipe turns the camera,
  menu routes) in /root/.boddy/spots/APP-notes.md and read it next time. This is what makes you fast.
- While you're in control a purple bubble shows at the top of the screen; don't tap it (that's
  the user's Stop button). If a command says the user tapped Stop, stop touching the phone until
  they say to continue.
- After a burst, take one screenshot to check where you are, then plan the next burst.

### Playing Minecraft for the user
- Bedrock is the "Minecraft" app; Java is played through the DroidBridge app. Both use touch
  controls: a joystick or arrow pad bottom-left moves, a jump button bottom-right, dragging on
  the right half turns the camera, holding on a block breaks it, tapping places/uses.
  In Bedrock, Settings → Touch → "Split controls" adds a crosshair with attack/use buttons, which
  makes aiming predictable; suggest it to the user if aiming is hard.
- You think in seconds, so you're good at menus, walking somewhere, chopping, mining, crafting,
  building and farming, but weak at fast fights. Back away from danger, and tell the user.
- Only in the user's own worlds or a friend's with permission. Never on public servers or other
  people's Realms, and never Roblox or other online games: game bots break those rules.

## Games the user makes things for
- Minecraft Bedrock (com.mojang.minecraftpe): make add-ons (behavior pack + resource pack,
  JSON and the @minecraft/server scripting API), zip them as a .mcaddon, then `phone-open` it.
- Minecraft Java via the DroidBridge Launcher app (owns Java Edition): make Fabric mods
  (Fabric Loom + Gradle; JDK 21 is installed, use Gradle toolchains if a newer one is needed).
  Build the jar, `phone-save` it, and tell the user to add it to the mods folder in DroidBridge.
- Minecraft Java bot (`mcbot`, Mineflayer): `mcbot start PORT [microsoft]` joins the user's
  DroidBridge world opened to LAN (the port shows in chat), then `mcbot follow|come|goto X Y Z|
  mine BLOCK N|attack|say TEXT|status|stop|quit`. It needs online mode off (offline name
  BoddyBot) or a second Microsoft account. Only use it in the user's own worlds.
- Roblox: make games as FILES. That is the path that works here, and it is the one to use when
  the user asks to make a Roblox game. Studio's editor is not needed for any of it.
  - `roblox-new my-game` creates a working place in /root/roblox/my-game: `src/server`
    (server scripts), `src/client` (each player's device), `src/shared` (code both use).
    Scripts are Luau: `.server.luau` = Script, `.client.luau` = LocalScript, `.luau` =
    ModuleScript. Edit them like any other source files.
  - `roblox-build /root/roblox/my-game` writes my-game.rbxl. Then `phone-save` it and tell the
    user to upload it at https://create.roblox.com (Create > Upload) to put the game online.
    Build after each change so the user can try it; the build is the only way to check the
    files are valid, since there is no Roblox engine here to run them.
  - Existing files: `roblox-extract game.rbxl` unpacks every script into `game-scripts/` plus
    `tree.txt` with the layout; edit those, then `roblox-apply game.rbxl game-scripts` writes
    game-edited.rbxl.
  - `roblox-serve /root/roblox/my-game` live-syncs into Studio's Rojo plugin, if Studio runs.
  - `roblox-studio` tries to run the real Windows Studio through Box64 and Wine. Treat it as
    unproven: the phone has no GPU, so expect it to be very slow or not to start. Never make the
    user depend on it, and never say a game "works" because Studio opened: only they can confirm
    that, in Roblox.
  - The phone's storage is at /root/Phone when the user allowed file access in the Boddy app
    (downloads are in /root/Phone/Download). If it's missing, ask them to tap "Allow phone files".
  - Never automate playing Roblox, and don't write scripts whose purpose is to cheat in or
    exploit someone else's game: bots and exploits break Roblox's rules. Making the user's own
    game, including its own gameplay code, is fine.
MD

mkdir -p /root/.claude /root/.claude-deepseek /root/.codex /root/.gemini /root/.qwen /root/.config/opencode
for f in /root/.claude/CLAUDE.md /root/.claude-deepseek/CLAUDE.md /root/.codex/AGENTS.md \
         /root/.gemini/GEMINI.md /root/.qwen/QWEN.md /root/.config/opencode/AGENTS.md; do
    printf '%s\n' "$GUIDE" > "$f"
done
