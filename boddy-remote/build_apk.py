#!/usr/bin/env python3
"""Adds Claude Remote to a Boddy APK.

Usage: build_apk.py IN.apk OUT-unsigned.apk VERSION_CODE
Then:  zipalign -P 16 -f 4 OUT-unsigned.apk aligned.apk && apksigner sign --ks KEY --out OUT.apk aligned.apk

Adds assets/boddy/{boddy-remote,remote-control.sh,boddy-guard.py,remote-setup.sh}, makes
make-launchers.sh (run at every desktop start) call remote-setup.sh, and bumps versionCode.
Every other entry is copied unchanged with its original compression method.
"""
import importlib.util, sys, zipfile
from pathlib import Path
here = Path(__file__).resolve().parent
src, out, version = Path(sys.argv[1]), Path(sys.argv[2]), int(sys.argv[3])

# set_version_code() from the Studio fix's rebuild_apk.py, which can't be imported (it runs on import).
def u32(b, o): return int.from_bytes(b[o:o + 4], 'little')
def set_version_code(b):
    h = int.from_bytes(b[10:12], 'little'); count = u32(b, 16); start = u32(b, 28); assert not u32(b, 24) & 256
    names = []
    for i in range(count):
        o = 8 + start + u32(b, 8 + h + 4 * i); n = int.from_bytes(b[o:o + 2], 'little')
        names.append(bytes(b[o + 2:o + 2 + n * 2]).decode('utf-16le'))
    changed = []; pos = 8
    while pos < len(b):
        typ = int.from_bytes(b[pos:pos + 2], 'little'); size = u32(b, pos + 4)
        if typ == 0x102 and names[u32(b, pos + 20)] == 'manifest':
            astart, asz, n = (int.from_bytes(b[pos + 24 + 2 * k:pos + 26 + 2 * k], 'little') for k in range(3))
            for i in range(n):
                off = pos + 16 + astart + i * asz
                if names[u32(b, off + 4)] == 'versionCode':
                    assert b[off + 15] in (16, 17); changed.append(u32(b, off + 16)); b[off + 16:off + 20] = version.to_bytes(4, 'little')
        pos += size
    assert len(changed) == 1 and changed[0] < version, changed
    return changed[0]

HOOK = '\n# Claude Remote and the crash guards (see remote-setup.sh).\nbash /opt/boddy/remote-setup.sh >/dev/null 2>&1 || true\n'
added = {'assets/boddy/' + n: (here / n).read_bytes() for n in ('boddy-remote', 'remote-control.sh', 'boddy-guard.py', 'remote-setup.sh')}
with zipfile.ZipFile(src) as z, zipfile.ZipFile(out, 'w') as w:
    names = set(z.namelist())
    template = z.getinfo('assets/boddy/make-launchers.sh')
    for info in z.infolist():
        if info.filename.startswith('META-INF/') or info.filename in added:
            continue
        data = z.read(info.filename)
        if info.filename == 'AndroidManifest.xml':
            data = bytearray(data); old = set_version_code(data); data = bytes(data)
        elif info.filename == 'assets/boddy/make-launchers.sh':
            text = data.decode()
            if 'remote-setup.sh' not in text:
                text = text.rstrip('\n') + '\n' + HOOK
            data = text.encode()
        w.writestr(info, data, compress_type=info.compress_type)
    for name, data in added.items():
        info = zipfile.ZipInfo(name, date_time=template.date_time)
        info.external_attr = 0o100755 << 16
        w.writestr(info, data, compress_type=template.compress_type)
print('versionCode %d -> %d; wrote %s' % (old, version, out))
