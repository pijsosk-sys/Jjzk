# Fortnite match-stability inside droidvm (v0.0.6)

Goal: get past the lobby and keep a **match** alive, so the host-side VNC
collector has live gameplay to read. Everything here is configured **inside the
guest Android**; droidvm only supplies the VM.

## What the APK tells us about the environment

- **Backend: Qualcomm Gunyah / KVM hardware virtualization** (`/dev/gunyah`,
  `/dev/kvm`, `edk2-gunyah.fd`). On an S24 Ultra (SD 8 Gen 3) the guest CPU runs
  **native** on the real hypervisor. This is the single biggest advantage:
  the emulator tells BattlEye relies on (TCG instruction timing, odd CPUID
  leaves, `ro.kernel.qemu`) are **absent** on a Gunyah guest. You are not
  fighting "emulator detection"; you are fighting "rooted VM identity."
- **Guest devices are all virtio-pci**: `virtio-gpu`, `virtio-net`, `virtio-blk`,
  `virtio-rng`, `virtio-sound`, `virtio-multitouch`, `virtio-keyboard`,
  `virtio-balloon`, `virtio-pmem`, `virtio-scsi`. These are the remaining VM
  signature (see rank 4).
- **Magisk is pre-baked** in the guest image. droidvm has **no** built-in
  Zygisk/DenyList/Shamiko/PlayIntegrity logic — that is yours to configure in
  the guest Magisk app.
- droidvm calls `resetProperties` / `resetPropertiesForNewStart` per VM start —
  it resets some props itself; yours must be applied after boot or via a module.

## What can kick you, ranked by likelihood — and the fix

### 1. Magisk / root is visible  (most common kick)
BattlEye enumerates `su`, Magisk's socket, mounts, and the manager package.
- Magisk app → **Zygisk ON**.
- **Enforce DenyList** and add: `com.epicgames.fortnite`, `com.epicgames.portal`,
  and any `com.epicgames.*` helper. DenyList covers child/zygote processes where
  BattlEye runs.
- Install **Shamiko** (makes DenyList a true blacklist + hides the list itself).
- **Hide the Magisk app** (random package name) via Magisk → Settings → Hide.

### 2. Play Integrity / attestation verdict
Epic + BattlEye query Play Integrity. A Gunyah guest has **no hardware
keystore**, so `STRONG` is unreachable — this is the ceiling.
- Install **PlayIntegrityFix** (chiteroman) + its companion — targets a
  `MEETS_DEVICE_INTEGRITY` / `BASIC` pass by spoofing a certified fingerprint.
- Verify with an integrity checker app: aim for **DEVICE** green. If Fortnite
  only requires BASIC/DEVICE you're through; if it hard-requires STRONG, no
  software fix clears it on this hardware (see ceiling).

### 3. Build fingerprint / system props look like a VM
- `resetprop` a **real, certified S24 fingerprint** so `Build.*` reads native.
  Keep it consistent with the PIF fingerprint (same device or they conflict):
  ```
  ro.product.model=SM-S928B
  ro.product.brand=samsung
  ro.product.manufacturer=samsung
  ro.product.device=e3q
  ro.board.platform=pineapple
  ro.product.name=e3qxxx
  ro.build.fingerprint=samsung/e3qxxx/e3q:14/UP1A.231005.007/...:user/release-keys
  ```
  Apply via a Magisk `post-fs-data`/`service` resetprop script or a props module,
  not adb (adb resetprop doesn't survive for all readers).

### 4. virtio-gpu renderer + virtio device tree  (hardest)
`GL_RENDERER` on a virtio-gpu guest reads like `virgl`/`virtio_gpu`, and
`/sys/bus/pci` + `/proc/*/maps` show virtio devices. BattlEye can read these.
- Partial mitigations only: a Magisk module that masks `/sys` virtio entries and
  hooks the GL strings, or a guest GPU config that reports a generic Adreno
  string. There is **no clean full hide** of virtio from userspace; this is the
  likeliest "loads then kicks mid-match" cause if ranks 1–3 are clean.
- If the kick survives all of 1–3, this is your suspect. Capture the exact kick
  code/time to confirm.

### 5. Missing sensors / telephony / real-device signals
A bare guest has no accelerometer, no IMEI, no SIM. Some anti-cheats weight this.
- A sensor-spoof Magisk module (feeds plausible accelerometer/gyro) and a
  telephony spoof reduce the signal. Lower priority than 1–4.

## Ordered setup (do in this sequence, test after each)

1. Boot guest. Confirm backend is Gunyah/KVM (droidvm VM info), not qemu-TCG.
2. Magisk: Zygisk ON, DenyList enforce + Fortnite/Epic added, Shamiko installed.
3. Hide Magisk app.
4. PlayIntegrityFix installed; verify DEVICE-integrity green.
5. resetprop S24 fingerprint matching PIF.
6. Reboot guest. Launch Fortnite.

## Test ladder — localize the kick

Run the **host-side VNC collector** (not the in-guest APK) so capture itself
isn't a variable. Then:

| Outcome | Meaning | Next |
|---|---|---|
| kicked at launch / "device not supported" | integrity/attestation wall (rank 2) | PIF tuning; if STRONG-required, ceiling |
| lobby OK, kicked **joining** match | BattlEye runtime caught root or VM (1,3,4) | re-check DenyList/Shamiko, then virtio |
| in match, kicked after N min | periodic deep scan | rank 4 (virtio) most likely |
| match holds | **done** | calibrate ROIs, collect |

Capture the exact kick: `adb logcat -d | grep -iE "battleye|fortnite|anticheat|integrity"`
right after a kick, and the on-screen error code.

## The honest ceiling

If Fortnite's current build **hard-requires STRONG Play Integrity**, a Gunyah
guest cannot produce a hardware-backed attestation, and no guest-side module
fixes that — it's a missing hardware key, not a software check. In that case the
match will not hold regardless of root hiding. Ranks 1,3,4,5 are fully winnable;
rank 2 at the STRONG tier is the one wall hardware sets. The test ladder tells
you within minutes which regime you're in.
