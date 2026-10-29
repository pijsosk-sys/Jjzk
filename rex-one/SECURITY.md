# Security audit of the REX One jar

Done for one.22 on the shipped `out/REX-One-1.21.11.jar`. Every one of its 1,483 classes was
read: each class's strings and its calls into Java and Minecraft were extracted and searched
(`tools`-style constant-pool dump). Everything that matched was decompiled and read in full. The three
bundled library jars were checked too.

## Verdict

**No RAT, no token or password stealer, no hidden webhook, no remote code, nothing that deletes
your files.** One thing that ran other programs on your computer is now switched off.

## What was checked, and what was found

| Looked for | Found | Status |
| --- | --- | --- |
| Reading your Minecraft login (session token, session ID) | Never read. Only your username and UUID, by the base finders, Skin Protect and the menu | safe |
| Hidden network traffic | Only: Discord Rich Presence (local Discord app), the DonutSMP auction API (Auction Sniper, with your own API key), Mojang's public skin API (Skin Protect), iTunes search (album art for Spotify HUD) | safe |
| Hidden Discord webhooks or Telegram bots | None. All 11 webhook features send only to a URL you type in, and every one starts empty. Telegram sending is an empty stub | safe |
| Encrypted or hidden strings | The `EncryptedString` helper is used only on plain text in the source; its decrypt-from-hidden form is never used | safe |
| Starting programs (PowerShell, cmd, shell) | **Spotify HUD ran PowerShell with "ExecutionPolicy Bypass" (or osascript on Mac) every few seconds** to read the playing song | **removed** |
| Loading code at runtime (class loaders, downloads) | None | safe |
| Native libraries, Unsafe, agents, scripts | None | safe |
| Deleting or overwriting files | Only its own config and schematic files. The old "self destruct" (overwrite the jar) is an empty stub | safe |
| Browser, cookie, Discord or launcher files | Never touched | safe |
| Sending money or chat on its own | Panic Pay (off, empty target), Auto Sell/Shop/TPA: only with your settings | safe |
| Proxy that could reroute your connection | The Proxy module starts empty and is off | safe |
| Bundled jars | netty-codec-socks and netty-handler-proxy 4.2.7: every class identical to Maven Central (only Fabric's `fabric.mod.json` added). baritone-api 1.17.0: standard Baritone, nothing networked | safe |

## Removed

- **Spotify HUD's PowerShell/osascript calls.** The build now runs `tools/Harden.java`, which
  empties every method in the client that starts another program and lists them in the build log
  (`harden: emptied ...`). If one ever appears that can't be emptied safely, the build fails. Spotify HUD
  still loads, but it no longer shows a song.

## Worth knowing

- **Fake Pay** exists to show a fake `/pay` to other players without paying. It can't harm your
  computer, but using it to trick people is scamming, and servers ban for it.
- The webhook features (base finders, Auto Log, Block Notifier, Player Detection, Cord Snapper,
  Spawner Protect, Auction Sniper) send your coordinates and other players' names to whatever URL
  *you* put in. Only use your own webhook.
- Many modules are cheats (combat, ESP, dupes, auto-sell). Servers ban for those whether or not
  they're safe for your computer.
