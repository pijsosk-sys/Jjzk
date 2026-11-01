# Studio launcher: selective package install

Patch for `assets/boddy/studio` (the Boddy Linux compatibility launcher).
Drop-in replacement: `patches/studio`. Diff against the launcher shipped in
`Boddy-studio-fix.apk`: `patches/studio-selective-install.patch`.

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
