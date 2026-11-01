# Studio launcher patches

Two patches for `assets/boddy/studio` (the Boddy Linux compatibility launcher),
against the copy shipped in `Boddy-studio-fix.apk`:

1. `studio-selective-install.patch` — install less (below)
2. `studio-gpu-default.patch` — draw on the phone's GPU by default ([below](#2-gpu-by-default))
3. `studio-dead-fflags.patch` — drop the FFlags Studio has no variable for ([below](#3-dead-fflags))
4. `studio-build-preset.patch` — a Build preset for when Claude is the one building ([below](#4-studio-mode-claude-building-in-studio))

and three outside the launcher, for Studio Mode:

- `roblox-studio-mcp.patch` — five tools that each take a whole job in one round trip
- `studio-app.sh.patch` — Build in the performance-preset menu
- `studio-mode.sh.patch` — the agent instructions rewritten for the new tools

Each patch has its file beside it as a drop-in (`patches/studio`,
`patches/roblox-studio-mcp`, `patches/studio-app.sh`, `patches/studio-mode.sh`).
The four launcher patches apply in order and reproduce `patches/studio` byte for
byte; each of the other three reproduces its own drop-in.

---

# 1. Selective package install

## What it changes

Roblox's Studio deployment is 34 packages listed in
`https://setup.rbxcdn.com/<version>-rbxPkgManifest.txt` (format `v0`, then four
lines per package: name, MD5, compressed size, unpacked size). The launcher
downloaded all of them whole. Measured on `0.745.0.7451137`
(`version-7062aec9dbe74b14`): **570 MiB download, 1083 MiB on disk.**

Three packages are mostly the same data repeated once per language, or font
weights for scripts a phone build does not need:

| package | download | on disk | content |
|---|---|---|---|
| `content-api-docs.zip` | 23.72 MiB | **233.00 MiB** | 34 JSON files, one per language, ~6.5 MiB each |
| `StudioFonts.zip` | 101.19 MiB | 127.71 MiB | 124.6 MiB of it is 7 `NotoSansCJK-*.ttc` weights at 16–19 MiB each |
| `content-qt_translations.zip` | 0.62 MiB | 2.12 MiB | `<module>_<lang>.qm` for 11 languages |

`setup.rbxcdn.com` is S3 and answers `206 Partial Content`, so instead of
downloading these whole, the launcher now reads each archive's central
directory over HTTP Range and fetches only the entries it keeps. Entries that
sit next to each other go in one request (up to 2 MiB of unwanted bytes carried
along rather than splitting), so `StudioFonts` is 3 requests, not 36.

`WebView2RuntimeInstaller.zip` is dropped outright: it is a Windows installer
for Studio's built-in sign-in browser, and sign-in here goes through the
phone's browser or Firefox (`setup_browser_login`).

Measured result, verified against the live CDN:

```
content-api-docs         keep  1/35  runs 1  wire   0.67 MiB (was  23.72)  disk   6.40 (was 233.00)
StudioFonts              keep 36/43  runs 3  wire  16.39 MiB (was 101.19)  disk  21.70 (was 127.71)
content-qt_translations  keep  3/34  runs 1  wire   0.55 MiB (was   0.62)  disk   0.00 (was   2.12)
```

**−108 MiB download, −335 MiB disk** → 462 MiB / 748 MiB.
With `BODDY_STUDIO_NO_PLUGINS=1`: 348 MiB / 634 MiB.

Pruned packages also unpack in the download pool instead of waiting their turn
on the main thread, and never put an archive on disk.

## `redist.zip` is required

It is not an installer. It holds the loose `msvcp140.dll`, `msvcp140_1/2.dll`,
`vcruntime140.dll`, `vcruntime140_1.dll`, `ucrtbase.dll`, `concrt140.dll`,
`vccorlib140.dll` and the `api-ms-win-*` shims, and `RobloxStudio.zip` ships
none of them (it has only `RobloxStudioBeta.exe`, `RobloxCrashHandler.exe`,
`NativeDialog.exe`, `StudioMCP.exe`). Dropping it breaks Studio. It is
deliberately absent from `UNUSED`.

## Integrity

The manifest MD5 covers a whole archive, so it cannot be checked when only part
is fetched. Each entry is verified against the CRC32 and the unpacked size in
the archive's own central directory, over TLS that is still verified. Packages
fetched whole keep their MD5 check, and that is all 31 of them — only the three
pruned packages take the weaker path.

## Fallbacks

Per package, falling back to the old whole-archive download when:

- Range is not honoured (`200` instead of `206`) — an HTTPS filter that strips
  it, for instance — which raises `RangeUnsupported`;
- the central directory does not parse;
- the rule would keep more than 70% of the archive's bytes anyway, so the extra
  requests and the lost MD5 check are not worth it.

A failure here costs only the behaviour the launcher already had.

## Settings

| | |
|---|---|
| `BODDY_STUDIO_LOCALE` / `~/.local/share/roblox-studio/locale` | language to install (default `en-us`); `en-us` is always kept as Studio's fallback |
| `BODDY_STUDIO_ALL_LOCALES=1` | install every language and font weight |
| `BODDY_STUDIO_NO_PLUGINS=1` | skip `BuiltInStandalonePlugins.zip` (114 MiB: Toolbox, Asset Manager, Animation Editor, Terrain tools) |

## Not verified

Written and measured on Linux against the live CDN: manifest parsing, ranged
fetch, run coalescing, entry CRCs, byte-exact output, the no-Range fallback and
rejection of a tampered entry. Not run on a phone, under proot, or through
Box64/Wine into Studio itself — the install path is the only thing touched, but
a real launch on device is untested.


---

# 2. GPU by default

## The problem

The launcher already had a complete Adreno GPU path: `/dev/kgsl-3d0` detection,
a KGSL-capable Turnip build, its own Vulkan ICD, a `vulkaninfo` probe, the
`MESA_VK_WSI_DEBUG=sw` workaround for VNC's missing DRI3, and
`GPU_FAILED_FILE` to back out after a crash. None of it ran unless someone
typed `--use-renderer=GPU`:

```python
GRAPHICS = {..., 'GPU': "DXVK on the phone's GPU (Adreno, experimental)"}
SETUPS   = (('DXVK',False,'fast'), ('DXVK',False,'strongmem'), ...)   # no 'GPU'
```

`GRAPHICS` is what can be chosen; `SETUPS` is what is tried. Every ordinary
launch rendered on the CPU through DXVK on lavapipe, on the same CPU already
translating x86 under Box64.

Second problem: `write_studio_settings(studio, renderer)` never read `renderer`.
Even a manual `--use-renderer=GPU` still got the `Potato` FFlags — 15 fps, the
3D view drawn at 90 thousand pixels and upscaled, quality level 1, texture
quality 0. Those exist because the CPU was drawing. On the GPU they throttle it
for nothing.

## What it changes

- `('GPU',False,'fast')` goes in at the head of `SETUPS`, so the GPU is tried
  first and the existing ladder handles failure: no Adreno or no Turnip answer
  → one line saying why, then DXVK this launch; a crash on it → `GPU_FAILED_FILE`
  → CPU next launch. No new failure mode.
- `kgsl_device()` is now checked **before** `ensure_graphics('GPU')`. That call
  apt-installs `TURNIP_PACKAGES` plus `vulkan-tools`, and it ran before anything
  looked for the device. Harmless while GPU was opt-in; with GPU leading the
  ladder it would have made every non-Adreno phone install Turnip once and then
  fall back. The check is two `os` calls and no network.
- `GPU_FLAGS` lifts the software-rendering compromises when the GPU is drawing:
  30 fps, quality level 5, texture quality 2, the 3D view at 720 thousand pixels
  instead of 90. The CPU still runs Lua, physics and the x86 translation, so the
  target stays well under 60.
- `VIDEO_MB_GPU = 1536` instead of the preset's 512, for `dxvk.conf`'s
  `dxgi.maxDeviceMemory` and wined3d's `VideoMemorySize`.
- `LP_NUM_THREADS` is no longer set in GPU mode — lavapipe is not drawing.
- `.choice-v6` resets the saved ladder step once, since a step number now names
  the setup below the one it used to.

A preset chosen by hand still wins, and `BODDY_STUDIO_GPU_FLAGS=0` keeps the
software settings on the GPU too.

## What is not changed, and why

`WINEESYNC` / `WINEFSYNC` would cut Wine's per-synchronisation server round
trips, which would help a thread-heavy program like Studio. They are
wine-staging patches and `WINE_BRANCH = 'stable'`, so setting them does
nothing. They would need a staging build first.

## Not verified

Checked here: the ladder order, that step 0 is GPU, that `GPU_FAILED_FILE`
demotes to DXVK, that a machine with no `/dev/kgsl-3d0` falls back without
touching apt or the network, that GPU mode writes 30 fps / 720 kpx, that DXVK
mode still writes 15 fps / 90 kpx, that a hand-picked preset and
`BODDY_STUDIO_GPU_FLAGS=0` both override it, and that `FFlagDebugGraphicsPreferD3D11`
is still forced.

Not checked: any of it on an actual Adreno phone. There is no GPU in this
container, so the framerate gain is reasoned from what the code does, not
measured. The `GPU_FLAGS` numbers are a starting point in one place, not tuned
against a device.


---

# 3. Dead FFlags

## How a flag can be checked

Roblox registers a fast variable with a macro that stores the name **without**
its type prefix, so `DFIntTaskSchedulerTargetFps` leaves `TaskSchedulerTargetFps`
in the binary as a NUL-terminated string. A name that does not appear at all is
not a variable Studio has, and a setting for it in `ClientAppSettings.json` is
read and discarded.

Checked against `RobloxStudioBeta.exe` from `0.745.0.7451137` (222 MB, PE32+
x86-64), looking for `b'<Name>\x00'`. Note that `strings` gives false negatives
here: `DebugForceMSAASamples` sits directly after a float in `.rdata` and comes
out glued to the preceding byte, so the raw byte search is the one to trust.

## Seven of the launcher's flags do not exist

```
FFlagDisablePostFx                     FIntRenderGrassDetailStrands
FIntRenderShadowIntensity              FFlagGlobalWindRendering
DFFlagDebugRenderForceTechnologyVoxel  DFIntTextureQualityOverride
DFFlagTextureQualityOverrideEnabled
```

None appear in the binary, and none appear among the 26,878 prefixed flags
Roblox pushes across `PCStudioApp`, `PCDesktopClient`, `PCClientBootstrapper`
and `StudioAppSettings`. Removing them changes no behaviour — they never had
any — but two of them were load-bearing in intent:

- **the lighting was never forced to voxel.** `DFFlagDebugRenderForceTechnologyVoxel`
  was meant to pin Studio to the cheapest lighting technology. It does nothing,
  so a place opens on whatever its own `Lighting.Technology` says, which can be
  ShadowMap or Future — far more expensive than voxel on a phone.
- **textures were never pinned to the lowest quality.** Both halves of that
  override are dead.

No replacement for either is in the binary. `Lighting.Technology` is a property
of a place now, not a fast variable, so forcing it would mean editing the place
rather than a flag.

## Candidates found, not wired in

These names **are** in the binary and look directly useful, but Roblox pushes no
value for them on any endpoint, so their type prefix cannot be confirmed — and a
wrong prefix is silently ignored, which looks identical to a flag that did
nothing:

```
RenderForceVideoMemorySize                       (would replace the DXVK maxDeviceMemory guess)
PerformanceControlLowPowerTextureQualityEnabled  (the modern texture-quality lever)
PerformanceControlLowPowerTextureQualityPercent
RenderShadowResolutionScaling
RenderPostFxBasePixelCount
```

Worth trying one at a time on a device, not worth guessing into the default
config.

## What the launcher writes now

14 flags in both GPU and CPU mode, every one verified present in the binary.
`DFIntTextureQualityOverride` also came out of `GPU_FLAGS`.

## Not verified

The prefixes on the flags that are kept (`DFInt` on `TaskSchedulerTargetFps`
and the rest) could not be confirmed from any endpoint either — Roblox pushes no
value for the debug and override class. Those come from the launcher as it was
and are left alone; only names absent from the binary were removed. Whether
removing the dead seven changes anything visible on a phone is untested, and the
expectation is that it does not.

---

# 4. Studio Mode: Claude building in Studio

The APK already shipped this: `studio-mode.sh` puts Studio on the left of the screen and Claude Code
on the right, joined by `roblox-studio-mcp` and `MCPStudioPlugin.rbxmx`, so Claude can read and build
in the open place. The server is a dependency-free Python port of Roblox's official
`studio-rust-mcp-server`, speaking the same protocol to the same plugin, with six tools:
`run_code`, `insert_model`, `get_console_output`, `start_stop_play`, `run_script_in_play_mode`,
`get_studio_mode`.

Every one of those is a round trip through emulated Studio: seconds each. So the cost of a build is
mostly the *number* of calls, and all six are small primitives — the existing `CLAUDE.md` could only
ask Claude to batch by hand.

## Five tools that take a whole job

| tool | replaces |
|---|---|
| `get_tree(path?, depth?, limit?)` | walking `GetChildren()` one `run_code` at a time |
| `get_properties(path, names?)` | one `run_code` per property |
| `set_properties(path, properties)` | one `run_code` per property, and hand-written Luau literals |
| `write_script(path, source, class?)` | a `.Source = "..."` assignment built inside `run_code` |
| `run_batch(commands[])` | N `run_code` calls |

**No plugin change.** Each is a `RunCode` task whose generated Luau prints a sentinel line with a
JSON result, read back out of the printed output — so anything the user's own code prints in the same
run is kept separate rather than confusing the parser. `MCPStudioPlugin.rbxmx` is untouched.

**Paths are lists**, `["Workspace","Model","Part"]`, not dotted strings: instance names may contain
dots, spaces and quotes, and the stub tree in the tests is literally named `Weird.Name "quoted"` to
keep that honest.

**Values are tagged** so a caller never writes Luau: `{"Vector3":[x,y,z]}`, `{"Color3":[r,g,b]}`
(0–255), `{"UDim2":[xs,xo,ys,yo]}`, `{"Enum":"Material.Plastic"}`.

**`write_script` exists because of quoting.** Putting a script's code inside a Luau string literal in
a `run_code` command breaks on a quote, a backslash, a `]]`, a nested long comment or a control
character. `lua_string()` escapes all of it, always as three-digit decimal escapes so a following
digit cannot extend one.

`roblox-studio-mcp --dump-luau <tool> '<json-args>'` prints the exact Luau a tool would send, for
checking by hand.

## The Build preset

For Studio Mode, where Claude is building and nobody is watching the 3D view:
`DFIntDebugDynamicRenderKiloPixels` drops to 40 thousand pixels, a sixth of Potato's area, and the
frame-rate target is deliberately left at 24.

That second half matters. `DFIntTaskSchedulerTargetFps` paces Studio's *whole* task scheduler, not
just drawing, and the MCP plugin's long poll and the Luau it runs are scheduled work too — so
lowering it to "stop rendering" would throttle the very thing being waited for. Cutting only the
pixel count takes the software rasteriser, which is what actually eats the processor, out of the way
of the Luau.

In the Roblox Studio app: Performance preset → Build. Put it back to Potato to look at the result.

## Verified

- the generated Luau is executed for real with `lua5.4` against a Roblox-shaped stub (`game`,
  `Instance.new`, `FindFirstChild`, `GetFullName`, `typeof`, `HttpService:JSONEncode`, `Vector3`,
  `Color3`, `Enum`), and each tool's JSON result checked;
- `write_script` round-trips a 297-byte script containing `"`, `\`, `]]`, `]=]`, a nested
  `--[==[ ]==]` comment, a NUL, byte 127, Cyrillic, `∑` and an emoji — **byte-identical**, and the
  reported length matches;
- replacing an instance of a different class at the same path works;
- end to end over the real MCP protocol: a subprocess server, the real HTTP bridge on 44755, and a
  fake plugin that long-polls and actually runs the Luau — `tools/list` returns 11 tools, all five
  new ones return parsed JSON, a missing instance comes back as `isError`, and bad arguments come
  back as a JSON-RPC error rather than reaching Studio;
- every flag the Build preset writes is present in `RobloxStudioBeta.exe`.

## Not verified

`lua5.4` is not Luau, so this checks syntax, escaping and logic — not Roblox semantics. In the stub,
reading a property an instance does not have returns nil instead of raising, so the `not_present`
branch of `get_properties` is exercised by construction rather than by a real failure. `loadstring`
is what `run_batch` and the error wrapper use; the plugin runs code in a context where it should be
available, but that is from the official server's behaviour, not something I ran in Studio. And none
of it has been through actual Studio on a phone.

---

# Built APK

`Boddy-studio-fix-patched.apk` — the uploaded `Boddy-studio-fix.apk` with every patch applied,
md5 `b06af8ac8707d24576c3251316b56f99`. Four assets replaced: `studio`, `roblox-studio-mcp`,
`studio-app.sh`, `studio-mode.sh`.

Rebuilt from the APK, not from source. All 600 entries keep their original compression
(`resources.arsc` stays STORED), the old `META-INF` signatures are stripped, then `zipalign -p -f 4`
and `apksigner sign`. v2 and v3 verify.

`Installer.copyScripts()` in `classes.dex` is called from `BoddyService.startDesktop`
unconditionally: it lists the `boddy` asset directory and overwrites each file in `/opt/boddy/`, with
no version gate. So reinstalling does replace all four on the next desktop start.

**It is signed with a new key**, so Android will not install it over an existing Boddy. Uninstalling
first wipes the app's data, which holds the Debian rootfs, the Wine prefix and any Studio projects.
Signing the aligned APK with the original `BODDY-TE` keystore instead makes it an ordinary update.

To test without reinstalling anything, the four files can be dropped in by hand from Boddy's
terminal — `copyScripts()` only overwrites names that exist as assets, so a copy under another name
survives:

```bash
install -m755 ~/Phone/Download/studio            /opt/boddy/studio-patched
install -m755 ~/Phone/Download/roblox-studio-mcp /opt/boddy/roblox-studio-mcp
install -m755 ~/Phone/Download/studio-mode.sh    /opt/boddy/studio-mode.sh
BODDY_STUDIO=/opt/boddy/studio-patched /opt/boddy/studio-app.sh
```

(The MCP server and `studio-mode.sh` do get overwritten on the next desktop start, so for those the
APK is the lasting fix.)

Not verified: the repack, alignment, signatures and asset contents are checked here; installing and
launching it on a phone is not.
