# localhost.lunar 2.4.3 — teardown

Static read of the shipped splits (manifest, dex, arm64 `.so`, assets). Nothing was run.

## Shape

A **Go proxy with a React Native UI**. Not an injector, not a pack writer.

| layer | what |
| --- | --- |
| UI | React Native + Expo, Hermes bytecode in `assets/index.android.bundle` (6.9 MB), `libreactnative.so`, `libhermesvm.so`, reanimated / screens / svg / gesture-handler |
| core | `libgojni.so`, **50 MB**, gomobile-bound Go. Module path `github.com/lunar-bedrock/lunar` |
| storage | MMKV (`libNitroMmkv.so`), Nitro modules |
| telemetry | Sentry + Firebase Crashlytics, own `…bedrock.com/api/v1/{analytics,metrics,log}` endpoints |
| billing | `libuniffi_cel.so` = `cel-interpreter-0.8.1` behind uniffi — a rules engine for paywall targeting, **not** game code |

## Go dependencies (from the symbol table)

| module | version | role |
| --- | --- | --- |
| `sandertv/gophertunnel` | v1.59.1-0.20260819 | Bedrock protocol: every `packet.*` type, login chain, encryption |
| `sandertv/go-raknet` | v1.15.2-0.20260705 | RakNet listener + dialer |
| `df-mc/dragonfly` | v0.10.14-0.20260508 | block/item registry, world math |
| `df-mc/go-playfab`, `df-mc/go-xsapi` | v2.0.3 / v2.0.4 | Xbox Live + PlayFab auth, so it can join online servers and realms as the real account |
| `pion/webrtc` + ice/turn/stun/dtls/srtp, `coder/websocket` | v4.2.16 | **NetherNet** — Bedrock's WebRTC transport, used for joining friends' worlds and realms |
| `oomph-ac/oomph` | v0.1.7-0.20260910 | the Oomph anticheat, vendored — movement/combat simulation reused in-proxy |
| `hashimthearab/*` | — | the developer's own module |
| `klauspost/compress`, `crypt2go`, `go-jose`, `go-oidc`, `segmentio`, `chewxy/math32`, `brentp/intintmap` | — | compression, crypto, token handling, hot-path math |

## Internal packages

```
lunar/internal/relay          proxy core: listen local, dial remote, pipeline both directions
lunar/internal/serverbound    client→server packet rewriting
lunar/internal/nethernetupstream   friend/realm joins over WebRTC
lunar/internal/packetdelay    held packets (backtrack, delay, blink)
lunar/internal/blockscan      chunk palette scanning — xray, chunk finders, ore/base search
lunar/internal/beamframe      box/beam drawing via fake actors
lunar/internal/debugui        ClientBoundDebugRenderer + DebugShape — the ESP renderer
lunar/internal/lookray        raycast for triggerbot / aim
lunar/internal/camera         camera packets (freecam-ish)
lunar/internal/inventory      serialized inventory transactions
lunar/internal/effectruntime  client-side effect state (no_blindness, slow_falling, anti_weather)
lunar/internal/module         module registry
lunar/internal/lunarui, colorpicker, i18n, lcmd, pinput, capability, shulkerglyph, bedrockdata
lunar/player/{control,form,settings,syncpolicy}
lunar/{world,entity,block,item,skin,schematic,session/authrepo,serverinfo,entitlement}
lunar/utils/{pmath,data,telemetry}
```

## How it renders without touching the client

Two mechanisms, both server-bound-free:

1. **`ClientBoundDebugRenderer` / `DebugShape`** — the vanilla client draws debug shapes (cubes, lines, text) sent by a server. The proxy forges these packets. That is player ESP, tracers, hole ESP, chunk borders, highlights.
2. **Fake actors** (`beamframe.addActor` → `packet.AddActor`) for beams and frames, plus `SpawnParticleEffect` where a shape won't do.

No renderer hook, no injection — the game draws it because the "server" asked for it.

## Movement

`motionfly`, `bunnyhop`, `step`, `velocity`, `waterwalk`, `scaffold` all exist and work: `PlayerAuthInput` is sent **by the client**, so a proxy rewrites position/rotation/input flags before they reach the server. Server-authoritative movement constrains this, it does not kill it — Oomph is vendored precisely to know where the limit is.

## Module list (104 i18n roots, run-on artifacts included)

```
afkclicker aimassist allchunkfinder anchoraura anticrystal autocart autoclicker autocrit
autocrystal autodig autoeat autoelytra autofish autolog autominer autopot autorefill
autorocket autosell autostealer autoswitch autototem autoweb backtrack blocktracers
bufferdoubleclick bunnyhop chunkborders chunkfinder commandautoswitch coordinates
cornerblocktracers cpsaimassist cpsmacedmg delay disabler dispenserchunkborders
doubleclick eatinganticrystal fasterautofish fastermodalerts fastinteract fullschematic
ghost heightkillaura hitbox holeesp horizontalshielddisabler hotbarhitbox itemesp java
killaura listedautominer macedmg maceswap modalerts motionfly newchunks nonestatchanger
occupiedafkclicker onlyautolog onlyspawnerprotect playeresp radiusnewchunks
radiusspawnerping realmdisabler relog scaffold schematic shielddisabler spam
spamautocrystal spawnerping spawnerprotect speedwaterwalk statchanger step suschunkfinder
tpaura triggerbot velocity walkautodig waterwalk xray
```

Plus an internal id blob: `auto_crystal no_invisible anti_weather no_blindness skin_stealer
stat_changer spawner_ping chunk_finder slow_falling double_chest`.

`suschunkfinder` carries the most settings of anything in the build — amethyst, dripstone,
deepslate, cobbled/rotated deepslate, end stone, bamboo, berries, growth sensitivity,
ignore trial chambers, ignore exposed, highlight Y and colour. That is base finding by
chunk-palette statistics, and it is the app's real selling point.

## What this means for a build of our own

Same stack, no second-guessing: Go + gophertunnel + go-raknet, dragonfly for the block
registry, pion for NetherNet, debug-renderer packets for drawing. The UI is a free choice —
their React Native layer is the heaviest part of the app and does nothing a native one
could not.
