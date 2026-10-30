# Studio Lite

Make Roblox games on your phone, in the Boddy desktop. Real Roblox Studio is a Windows program,
and on the phone it would have to run under emulation with no graphics chip, which never worked.
Studio Lite is a small Roblox maker that runs natively instead.

## What it does

- **Explorer:** your game's tree, like in Studio (Workspace, ServerScriptService, StarterPlayer…).
- **3D view:** drag to turn, pinch or scroll to zoom, tap a part to select it, then Move / Rotate /
  Size it with the handles.
- **Properties:** name, position, rotation, size, colour, material, shape (block, ball,
  cylinder), transparency, anchored, can collide.
- **Add:** parts, balls, cylinders, spawn locations, models, folders, Scripts, LocalScripts,
  ModuleScripts.
- **Script editor:** line numbers, Tab, keeps your indent. **Check** finds syntax errors in every
  script with the real Luau compiler (Lune) and marks them red.
- **Export:** saves `<game>.rbxlx` to the phone's Downloads. Roblox Studio on a computer opens it.
- **Publish:** puts the game live on Roblox with an Open Cloud API key. **Play** then opens it
  in the Roblox app on the phone.
- Saves by itself as you work, to `~/Projects/Studio Lite/<game>/project.json`.

## What it can't do

- No playtesting inside Studio Lite: publish, then play in the Roblox app.
- Only the object types listed above. No terrain, GUIs, meshes, sounds or plugins yet.
- Opening an existing `.rbxl` game isn't supported. Roblox Tools can still pull the scripts out.

## Install on the phone

1. Save `dist/install-studio-lite.sh` to the phone's Downloads.
2. In Boddy, open Terminal and run: `bash ~/Phone/Download/install-studio-lite.sh`
3. Open **Studio Lite** on the desktop.

Running it again updates Studio Lite and keeps your games.

## Publishing

1. Make a game at create.roblox.com and open it. Copy its **Universe ID** and **Place ID**.
2. create.roblox.com → Open Cloud → API Keys → Create. Add **universe-places** with **write**
   for that game. Copy the key.
3. In Studio Lite, tap ⚙ and paste both IDs and the key. It's stored only on the phone
   (`~/.config/studio-lite/api-key`, readable only by you).
4. Tap **Publish**, then **Play**.

## For developers

- `server.py`: a local server on 127.0.0.1 with a per-start token. It stores projects, writes
  `.rbxlx`, runs `check.luau` with Lune, and publishes through Open Cloud
  (`POST /universes/v1/{u}/places/{p}/versions?versionType=Published`).
- `web/`: the page (plain JS modules, three.js 0.170 in `web/vendor`, MIT).
- `studio-lite-app.sh`: the desktop icon. It starts the server once and opens Firefox with its
  own profile (WebGL forced on, Firefox sandboxes off for proot).
- Tests: `LUNE=/path/to/lune python3 -m unittest discover -s tests -v`. Lune's Roblox file reader
  reads the generated place files back, so every property, material, shape and rotation is
  checked against Roblox's own format, not against this code.
- `make-installer.sh` writes `dist/install-studio-lite.sh`.
