#!/usr/bin/env python3
"""
Host-side HUD collector for droidvm (cn.classfun.droidvm).

Taps the QEMU guest display from OUTSIDE the guest by connecting a second RFB
(VNC) client to the loopback VNC server droidvm runs for the guest. Anti-cheat
(BattlEye/EAC) lives INSIDE the qemu guest; an RFB client reading frames from
the host's VNC server is invisible to it -- no MediaProjection, no injection,
nothing in the guest process space.

Pipeline:  VNC frame -> crop HUD regions -> tesseract OCR -> JSON -> WebSocket sink.

Reach the port from the phone over adb:
    adb logcat | grep -i "auto-assigned VNC port"       # shows PORT
    adb forward tcp:5900 tcp:PORT                        # bridge to your PC
Then run against 127.0.0.1:5900 with the VM's VNC password.

Deps:  pip install asyncvnc numpy pillow pytesseract websockets
       plus the tesseract binary (apt install tesseract-ocr / brew install tesseract)

required: --password  (droidvm's auto-generated per-VM VNC password; see README)
"""

import argparse
import asyncio
import json
import re
import sys
import time

import numpy as np
import pytesseract
from PIL import Image

import asyncvnc
import websockets

DIGITS = re.compile(r"\d{1,4}")

# HUD regions as fractions of the full frame (x0, y0, x1, y1), 0..1.
# Defaults target Fortnite Mobile landscape HUD. CALIBRATE per guest resolution:
# screenshot one match, measure each value's pixel box, divide by width/height.
DEFAULT_ROIS = {
    "health":  [0.040, 0.885, 0.120, 0.935],
    "shield":  [0.040, 0.840, 0.120, 0.888],
    "wood":    [0.820, 0.050, 0.900, 0.110],
    "stone":   [0.900, 0.050, 0.980, 0.110],
    "metal":   [0.740, 0.050, 0.820, 0.110],
    "players": [0.880, 0.020, 0.985, 0.055],
}

CLAMP = {"health": (0, 100), "shield": (0, 100)}


def load_rois(path):
    if not path:
        return DEFAULT_ROIS
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def read_roi(frame: np.ndarray, box) -> int | None:
    h, w = frame.shape[0], frame.shape[1]
    x0 = int(box[0] * w); y0 = int(box[1] * h)
    x1 = int(box[2] * w); y1 = int(box[3] * h)
    x0 = max(0, min(x0, w - 1)); x1 = max(x0 + 1, min(x1, w))
    y0 = max(0, min(y0, h - 1)); y1 = max(y0 + 1, min(y1, h))
    crop = frame[y0:y1, x0:x1, :3]
    if crop.size == 0:
        return None
    # upscale 3x + grayscale helps tesseract on small HUD glyphs
    img = Image.fromarray(crop).convert("L").resize(
        ((x1 - x0) * 3, (y1 - y0) * 3), Image.LANCZOS
    )
    txt = pytesseract.image_to_string(
        img, config="--psm 7 -c tessedit_char_whitelist=0123456789"
    )
    m = DIGITS.search(txt)
    return int(m.group()) if m else None


def extract(frame: np.ndarray, rois) -> dict:
    hud = {"ts": int(time.time() * 1000)}
    for name, box in rois.items():
        n = read_roi(frame, box)
        if n is not None and name in CLAMP:
            lo, hi = CLAMP[name]
            n = max(lo, min(n, hi))
        hud[name] = n
    return hud


async def sink_sender(queue: asyncio.Queue, url: str):
    while True:
        try:
            async with websockets.connect(url) as ws:
                while True:
                    line = await queue.get()
                    await ws.send(line)
        except Exception as e:
            print(f"[sink] reconnect ({e})", file=sys.stderr)
            await asyncio.sleep(2)


async def run(args):
    rois = load_rois(args.rois)
    queue: asyncio.Queue = asyncio.Queue(maxsize=64)
    sender = asyncio.create_task(sink_sender(queue, args.sink))
    try:
        while True:
            try:
                async with asyncvnc.connect(
                    args.host, args.port, password=args.password
                ) as client:
                    print(f"[vnc] connected {args.host}:{args.port}", file=sys.stderr)
                    while True:
                        frame = await client.screenshot()  # numpy HxWx4 RGBA
                        hud = extract(np.asarray(frame), rois)
                        line = json.dumps(hud)
                        print(line, flush=True)
                        if queue.full():
                            queue.get_nowait()
                        queue.put_nowait(line)
                        await asyncio.sleep(args.interval)
            except Exception as e:
                print(f"[vnc] reconnect ({e})", file=sys.stderr)
                await asyncio.sleep(2)
    finally:
        sender.cancel()


def main():
    p = argparse.ArgumentParser(description="droidvm host-side HUD collector")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=5900)
    p.add_argument("--password", required=True, help="droidvm VM VNC password")
    p.add_argument("--sink", default="ws://127.0.0.1:8765")
    p.add_argument("--interval", type=float, default=0.5, help="seconds between reads")
    p.add_argument("--rois", default=None, help="optional rois.json path")
    args = p.parse_args()
    try:
        asyncio.run(run(args))
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
