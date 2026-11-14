# FortCollector — host-side (droidvm) collector

Reads the Fortnite HUD from **outside** the QEMU guest that droidvm
(`cn.classfun.droidvm`) runs, so in-guest anti-cheat (BattlEye/EAC) never
sees a capture. No MediaProjection, no injection, no outer-device root.

## How droidvm exposes the guest display

droidvm boots a full `qemu-system-aarch64` guest (Magisk pre-baked) and renders
its screen with a **VNC server on the outer Android's loopback**:

```
-vnc 127.0.0.1:PORT,password-secret=vnc-password
```

- `PORT` is auto-assigned per VM (logged: `VM %s: auto-assigned VNC port %d`).
- The password is **auto-generated per VM** and held by the app.

The app's own `VncClient` connects there to show you the screen. A *second* RFB
client on that same loopback port reads the identical frames — invisible to the
guest, because the server lives in the host process, not the guest.

## Getting the port (no root)

```bash
adb logcat -d | grep -i "auto-assigned VNC port"   # -> PORT
adb forward tcp:5900 tcp:PORT                       # bridge guest VNC to your PC
```

## The password — the one lock

The RFB server requires auth, and the password is not logged and not readable
from a release app's private dir without root. Two ways:

1. **Patched droidvm (clean path):** rebuild the APK with the VM-launch args
   changed to drop `password-secret` (or pin a known password) and bind a fixed
   port. You install the patched build; nothing else on the phone changes.
   *Blocked: the uploaded APK split is missing part2 — need it to repackage.*
2. **If you can recover it** (rooted outer, or a build that prints it), pass it
   straight to `--password`.

## Run

```bash
pip install asyncvnc numpy pillow pytesseract websockets
# tesseract binary: apt install tesseract-ocr  /  brew install tesseract

python3 collector_vnc.py \
    --host 127.0.0.1 --port 5900 \
    --password <VM_VNC_PASSWORD> \
    --sink ws://127.0.0.1:8765 \
    --interval 0.5
```

JSON lines to stdout and to the sink:

```json
{"ts":1731600000000,"health":87,"shield":50,"wood":320,"stone":110,"metal":45,"players":63}
```

## Calibrate the ROIs (decides if the numbers are real)

`DEFAULT_ROIS` in `collector_vnc.py` are fractional guesses. Screenshot one real
match at the guest's resolution, measure each value's pixel box, divide by
width/height, and either edit the dict or pass a `--rois rois.json` of the same
shape. Wrong boxes = empty or wrong reads.
```
