# Studio launcher patches

Two patches for `assets/boddy/studio` (the Boddy Linux compatibility launcher),
against the copy shipped in `Boddy-studio-fix.apk`:

1. `studio-selective-install.patch` — install less (below)
2. `studio-gpu-default.patch` — draw on the phone's GPU by default ([below](#2-gpu-by-default))
3. `studio-dead-fflags.patch` — drop the FFlags Studio has no variable for ([below](#3-dead-fflags))
4. `studio-build-preset.patch` — a Build preset for when Claude is the one building ([below](#4-studio-mode-claude-building-in-studio))
5. `studio-apt-expired-lists.patch` — recover when apt refuses a package list for its date ([below](#5-apt-refusing-a-signed-list-for-its-date))
6. `studio-signin-default.patch` — sign in through the phone's browser by default ([below](#6-publishing-without-signing-studio-in))

and three outside the launcher, for Studio Mode:

- `roblox-studio-mcp.patch` — five tools that each take a whole job in one round trip
- `studio-app.sh.patch` — Build in the performance-preset menu
- `studio-mode.sh.patch` — the agent instructions rewritten for the new tools
- `make-launchers.sh.patch` — `roblox-publish` on PATH

and one new file:

- `roblox-publish` — upload a place to Roblox with no Studio sign-in ([below](#6-publishing-without-signing-studio-in))

Each patch has its file beside it as a drop-in (`patches/studio`,
`patches/roblox-studio-mcp`, `patches/studio-app.sh`, `patches/studio-mode.sh`).
The six launcher patches apply in order and reproduce `patches/studio` byte for
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

# 5. apt refusing a signed list for its date

## From a real launch

A launch on an Adreno 750 phone got the GPU and then stalled here:

```
GPU: Turnip Adreno (TM) 750 (Turnip 26.3.0-devel-20260824)
Studio setup 1 of 8: DXVK on the phone's GPU (Adreno, experimental), fast emulation, AVX hidden
Studio settings: GPU (30 fps, 3D view drawn at 720 thousand pixels)
...
Hit:2 http://deb.debian.org/debian trixie-updates InRelease
E: Release file for .../trixie-updates/InRelease is expired (invalid since 9h 10min 29s).
E: Release file for .../trixie-security/InRelease is expired (invalid since 13h 28min 21s).
Some package lists failed to download; retrying in 20 s
```

Checked against the mirror at the time: the live files were issued that same day and valid for
another week, so nothing on Debian's side was expired. The copies the phone was working from were
stale — apt's lists travel inside the prebuilt rootfs image, an InRelease file is valid for about a
week, and a cached copy (or one a caching proxy still answers with, which is what `Hit:` means) goes
out of date while its signature stays perfectly good.

`apt_install` retried the identical command three times with 20 s between, which cannot fix a date,
then ran the install anyway — and `ensure_graphics('GPU')` raises if that install fails, taking the
launch with it.

## What it does now

On seeing `Release file ... is expired` or `is not valid yet`:

1. says so, instead of looking like a network problem;
2. **reports the phone's clock against the server's** `Date` header. A clock days ahead makes every
   current list look expired — and breaks HTTPS besides — so this separates "your lists are stale"
   from "your clock is wrong" in one line rather than leaving it a mystery;
3. deletes `/var/lib/apt/lists` so apt cannot fall back on the stale copy it came with;
4. fetches again with `Acquire::Check-Valid-Until=false`, and carries that into the install.

No sleeps on this path: one retry, immediately.

**What relaxing that check costs.** The GPG signature is still verified; only "not older than
allowed" is dropped. That is the part which stops an old, signed list being replayed to hold a system
on known-vulnerable versions. It is the narrowest way past a stale image and it is not a reason to
turn signature checking off — which this does not do.

Also now: `apt-get` output is streamed as it arrives *and* captured, so the launcher log keeps live
progress and can still read back why a run failed.

## Verified

The regex matched against the real log text above; that it ignores an ordinary mirror hiccup
(`File has unexpected size`) and catches `is not valid yet`; `run_logged_output` streaming and
capturing both streams with the right exit code; the clock comparison against a live server; and the
decision path with apt stubbed — exactly two update attempts, no sleeps, lists cleared once, the flag
carried into the install.

## Not verified

Not run against a genuinely expired list on a phone. The recovery is reasoned from the error text and
exercised with apt stubbed out, not reproduced end to end.

---

# 6. Publishing without signing Studio in

## The problem

Studio runs, draws and edits, but it is signed out, and a Studio that cannot sign in cannot publish.
Its built-in login needs WebView2, which does not run under Wine. The launcher's way around that is
"Login via Browser", and on the 2026-11-01 run it was never reached:

```
=== sign-in steps since this launch ===
none: no sign-in page was opened and no sign-in came back from a browser
```

For someone whose only machine is the phone, an offline Studio is an editor with no way out.

## Two changes

### Sign-in defaults to the phone's browser

`signin_browser()` returned `firefox` unless told otherwise. Firefox inside Boddy costs about three
more Linux processes, and the session monitor on that same run showed **29 of Android's 32** already
in use with Studio alone — `open_in_firefox()` even stops to warn about it. Going over makes Android
close Boddy, Studio included.

The phone's own browser costs no Linux processes: the link goes out through `boddy-ctl phone-open`,
Roblox sends the browser to a `roblox-studio-auth:` link, and the Boddy app's `StudioSignInActivity`
hands it back. That is now the default. `open_in_browser()` still falls back to Firefox when the
phone cannot be reached, so nothing is lost where it already worked.

### `roblox-publish`: upload with no sign-in at all

Open Cloud needs no Studio session — an API key and an HTTPS request.

```
roblox-publish --setup                 save the key, Universe ID and Place ID
roblox-publish place.rbxl              publish it live
roblox-publish place.rbxl --save-only  a saved version: Studio sees it, players keep the live one
roblox-publish --check                 is the saved key still good
```

So the loop becomes: build in Studio signed out → **File → Save to File** → `roblox-publish` → live.
Claude can run it too, through Studio Mode.

**Which endpoint.** Documentation disagrees with itself here: the usage guides give
`apis.roblox.com/universes/v1/{universe}/places/{place}/versions`, while a `llms.txt` page gives
`apis.roblox.com/cloud/v2/...`. Settled by asking the live API with a deliberately bad key — v1
answers `401 Invalid API Key`, so the route exists and only the key was wrong, and cloud/v2 answers
`404`. The v1 path is the one used.

**The key** is read from a prompt and written to `~/.config/roblox-publish/credentials` with mode
`0600` inside a `0700` directory, the mode set at `os.open` time so it is never briefly readable by
others. It is never passed as a command-line argument, where any process on the system could read it
out of `/proc`.

Each HTTP status gets the answer that actually fixes it: `403` names the `universe-places` system,
the Write operation, the per-experience Access Permission and the IP restriction that a phone's
changing address trips; `401` covers a truncated or expired key; `404` the wrong IDs.

**What still needs the website once:** creating the experience, and making the key. Both work in the
phone's own browser at `create.roblox.com`. After that, publishing never touches it again.

## Verified

The two endpoints probed live (401 vs 404); `--setup` writing `0600` inside `0700`; `--check`,
a real upload attempt, an empty file, a wrong extension and a missing file each giving the right
message and exit code; the default sign-in browser now `phone`, `BODDY_STUDIO_SIGNIN` still
overriding it, and an unknown value falling back rather than breaking.

## Not verified

No publish with a real key — I have no Roblox account or experience, so every call here was rejected
at the key. The success path (`200`, reading `versionNumber`) is written to the documented response
shape and has not been seen. The phone-browser sign-in default is logic I tested, not a sign-in I
completed.

---

# What a real launch confirmed

From the 2026-11-01 log and screenshot on an Adreno 750 phone:

```
GPU: Turnip Adreno (TM) 750 (Turnip 26.3.0-devel-20260824)
Studio setup 1 of 8: DXVK on the phone's GPU (Adreno, experimental), fast emulation, AVX hidden
Studio settings: GPU (30 fps, 3D view drawn at 720 thousand pixels; set a preset by hand to override)
```

`/dev/kgsl-3d0` opened, Turnip bound and `vulkaninfo` named the real device; the ladder started at
step 1 of 8 (the GPU, which was not in the ladder before); `GPU_FLAGS` applied instead of Potato's
15 fps / 90 thousand pixels; Wine 11.0 under Box64. The screenshot shows Studio fully drawn —
viewport, Explorer, Properties, Toolbox — so the GPU path reaches a working editor.

Still unmeasured: the frame rate in use. Still unseen: a completed sign-in, and a publish.

Also from the session monitor: 29 of Android's 32 processes in use, with three `bash`, three
`studio-app.sh` and three `sleep` among them. Android 12+ stops the app above 32. Developer options
→ Disable child process restrictions removes the ceiling.

---

# Built APK

`Boddy-studio-fix-patched.apk` — the uploaded `Boddy-studio-fix.apk` with every patch applied,
md5 `406eed61f1814d6fd0ee8273e6e312f6`. 601 entries: five assets replaced (`studio`,
`roblox-studio-mcp`, `studio-app.sh`, `studio-mode.sh`, `make-launchers.sh`) and one added
(`roblox-publish`).

Rebuilt from the APK, not from source. All original entries keep their compression
(`resources.arsc` stays STORED), the old `META-INF` signatures are stripped, then `zipalign -p -f 4`
and `apksigner sign`. v2 and v3 verify.

`Installer.copyScripts()` in `classes.dex` is called from `BoddyService.startDesktop`
unconditionally: it lists the `boddy` asset directory and overwrites each file in `/opt/boddy/`, with
no version gate — and because it lists the directory rather than a fixed set of names, the added
`roblox-publish` is copied out too.

**It is signed with a new key**, so Android will not install it over an existing Boddy. Uninstalling
first wipes the app's data, which holds the Debian rootfs, the Wine prefix and any Studio projects.
Signing the aligned APK with the original `BODDY-TE` keystore instead makes it an ordinary update.

Not verified: the repack, alignment, signatures and asset contents are checked here; installing and
launching it on a phone is not.
