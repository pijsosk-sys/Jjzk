# Getting Fortnite to run in the droidvm guest

Everything here runs **inside the droidvm guest Android**, from its Magisk app
and a terminal (the guest's own, or `adb` into the guest). The outer S24 is only
the host. Do the steps in order; test after each so you know what moved.

## 0. Prereqs
- droidvm installed and a guest Android booted, backend = **Gunyah/KVM** (check
  the VM info; not qemu-TCG).
- Magisk present in the guest (droidvm ships it pre-baked). Open the Magisk app.

## 1. Flash the identity module (provided)
`forthide.zip` spoofs the guest's device identity props to a certified S24 Ultra.
It deliberately **leaves `ro.build.fingerprint` to PlayIntegrityFix** so the two
don't fight.

- Magisk app → Modules → Install from storage → pick `forthide.zip` → reboot guest.
- Verify after reboot: `getprop ro.product.model` → `SM-S928B`.

## 2. Zygisk + DenyList + Shamiko  (hides root)
- Magisk → Settings → **Zygisk: ON** → reboot.
- Magisk → Settings → **Configure DenyList** → enable, then tick:
  - `com.epicgames.fortnite`
  - `com.epicgames.portal`
  - any other `com.epicgames.*`
- Install **Shamiko** (turns DenyList into a true blacklist and hides it):
  - Source (grab on the phone, GitHub works there):
    `https://github.com/LSPosed/LSPosed.github.io/releases` → latest Shamiko zip.
  - Magisk → Modules → Install from storage → Shamiko zip → reboot.
  - With Shamiko installed, **leave "Enforce DenyList" UNCHECKED** (Shamiko’s
    requirement — it reads the list but enforces its own way).

## 3. Hide the Magisk app
- Magisk → Settings → **Hide the Magisk app** → give it a random name.

## 4. PlayIntegrityFix  (integrity verdict)
- Source (on the phone): `https://github.com/chiteroman/PlayIntegrityFix/releases`
  → latest `PlayIntegrityFix_vXX.zip`. (Companion/fork builds also fine.)
- Magisk → Modules → Install from storage → reboot.
- Verify: install an integrity checker (e.g. "Play Integrity API Checker"),
  run it, aim for **DEVICE** green. If only BASIC passes, Fortnite may still
  accept it — test anyway.

## 5. Launch
- Install the Epic Games App + Fortnite in the guest (you already had it in the
  lobby, so it's there).
- Start the **host-side collector** first so capture isn't a variable:
  ```
  adb forward tcp:5900 tcp:<PORT>      # PORT from get_vnc_creds.sh or droidvm UI
  python3 ../host/collector_vnc.py --host 127.0.0.1 --port 5900 \
      --password '<PASSWORD>' --sink ws://127.0.0.1:8765
  ```
- Open Fortnite → **queue a normal match.**

## 6. Read the result (the test ladder)

| What happens | Which rank bit you | Do next |
|---|---|---|
| kicked at launch / "device not supported" | 2 integrity | tune PIF; if STRONG-required → hardware ceiling |
| lobby OK, kicked **joining** match | 1 root or 3 props still visible | recheck DenyList/Shamiko, confirm forthide props |
| in a match, kicked after minutes | 4 virtio GPU/devices | virtio masking (hardest) |
| **match holds** | — | calibrate ROIs in collector_vnc.py, collect |

Right after any kick, grab:
```
adb logcat -d | grep -iE "battleye|epic|fortnite|anticheat|integrity|deny"
```
and note the on-screen error code. Send me both — that pins the next move.

## Note
This is the full guest-side hardening chain. Ranks 1/3 are handled by forthide +
Zygisk/DenyList/Shamiko, rank 2 by PIF (up to the STRONG hardware ceiling),
rank 4 (virtio) is the remaining hard problem if the match loads then drops.
See `../MATCH_STABILITY.md` for the detection map behind each step.
