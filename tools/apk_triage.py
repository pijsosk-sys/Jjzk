#!/usr/bin/env python3
"""
APK triage for bedrock utility clients.

Answers one question before any decompile: HOW does this app touch Minecraft?
  pack-writer  -> ships .mcpack/.mcaddon + scripting API js, drops them in the game's data dir
  proxy        -> VpnService or a local server, intercepts/rewrites the bedrock protocol
  native       -> own .so, in-process work (needs root/ptrace or a repacked game)
  launcher     -> only starts com.mojang.minecraftpe / manages files

usage:
  python3 tools/apk_triage.py base.apk [split_config.arm64_v8a.apk ...] [-o outdir]
  python3 tools/apk_triage.py app.xapk          # zip-of-apks, handled
"""
import os, re, sys, zipfile, hashlib, argparse, shutil, subprocess, collections

PERM_RE = re.compile(rb'android\.permission\.[A-Z_]+')
PKG_RE  = re.compile(rb'com\.mojang\.[a-z]+')

SIGNALS = {
    "proxy.vpn":        [b'BIND_VPN_SERVICE', b'VpnService', b'android/net/VpnService'],
    "proxy.raknet":     [b'RakNet', b'raknet', b'OfflinePing', b'UnconnectedPing', b'OpenConnectionRequest'],
    "proxy.bedrock":    [b'McpeLogin', b'StartGamePacket', b'MovePlayerPacket', b'LevelChunkPacket',
                         b'protocol_version', b'ProtocolInfo', b'cloudburst', b'nukkit', b'bedrock-protocol'],
    "proxy.local":      [b'127.0.0.1', b'0.0.0.0', b'19132', b'ServerSocket', b'DatagramSocket'],
    "crypto.session":   [b'ECDSA', b'ES384', b'secp384r1', b'x5u', b'JWT', b'jwt', b'identityPublicKey'],
    "pack.files":       [b'.mcpack', b'.mcaddon', b'.mcworld', b'behavior_packs', b'resource_packs',
                         b'manifest.json', b'world_behavior_packs.json'],
    "pack.script":      [b'@minecraft/server', b'minecraft:server', b'system.runInterval', b'scriptAPI',
                         b'GameTest', b'mojang-minecraft'],
    "fs.gamedir":       [b'com.mojang.minecraftpe', b'games/com.mojang', b'minecraftpe/files',
                         b'DocumentFile', b'ACTION_OPEN_DOCUMENT_TREE', b'StorageVolume'],
    "native.inject":    [b'ptrace', b'PTRACE_ATTACH', b'/proc/self/maps', b'dlopen', b'libminecraftpe',
                         b'inprocess', b'frida', b'LD_PRELOAD', b'xhook', b'Dobby', b'substrate'],
    "root":             [b'su -c', b'/system/bin/su', b'Superuser', b'magisk', b'Magisk', b'RootBeer'],
    "pack.hardening":   [b'com.secneo', b'com.stub.StubApp', b'libjiagu', b'DexGuard', b'bangcle',
                         b'libDexHelper', b'libexec.so', b'o-llvm'],
    "net.backend":      [b'firebase', b'https://', b'api_key', b'admob', b'applovin', b'unityads'],
    "antidebug":        [b'TracerPid', b'isDebuggerConnected', b'IsDebuggerPresent', b'android.os.Debug'],
}

def sha(p):
    h = hashlib.sha256()
    with open(p,'rb') as f:
        for b in iter(lambda: f.read(1<<20), b''): h.update(b)
    return h.hexdigest()[:16]

def expand(paths, work):
    """xapk/apks containers -> the apks inside them."""
    out = []
    for p in paths:
        if p.lower().endswith(('.xapk','.apks','.apkm','.zip')):
            d = os.path.join(work, 'container_' + os.path.basename(p))
            os.makedirs(d, exist_ok=True)
            with zipfile.ZipFile(p) as z:
                for n in z.namelist():
                    if n.lower().endswith('.apk'):
                        z.extract(n, d); out.append(os.path.join(d, n))
            if not out:          # slim bundle: dex/so/assets, no nested apk files
                out.append(p)
        else:
            out.append(p)
    return out

def manifest_strings(z):
    # a slim bundle holds base.apk/AndroidManifest.xml instead of one at the root
    names = [n for n in z.namelist() if n.endswith('AndroidManifest.xml')]
    if not names: return [], []
    raw = b''.join(z.read(n) for n in names)
    # binary AXML: pooled strings are UTF-16LE; this is grep, not a parser.
    flat = raw.replace(b'\x00', b'')
    perms = sorted(set(m.decode() for m in PERM_RE.findall(flat)))
    pkgs  = sorted(set(m.decode() for m in PKG_RE.findall(flat)))
    return perms, pkgs

def scan(path, hits, inventory):
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        inventory['entries'] += len(names)
        for n in names:
            low = n.lower()
            tail = n.split('.apk/', 1)[1] if '.apk/' in n else n
            low = tail.lower()
            if tail.startswith('lib/'):              inventory['libs'].add(tail)
            if low.endswith(('.mcpack','.mcaddon','.mcworld','.mctemplate')): inventory['packs'].add(n)
            if low.endswith('.js') and low.startswith('assets/'):             inventory['js'].add(tail)
            if re.fullmatch(r'classes\d*\.dex', tail): inventory['dex'].add(n)
            if low.endswith('manifest.json'):        inventory['pack_manifests'].add(n)
        perms, pkgs = manifest_strings(z)
        inventory['perms'].update(perms)
        inventory['mojang_refs'].update(pkgs)
        for n in names:
            if n.endswith('/'): continue
            try: data = z.read(n)
            except Exception: continue
            if len(data) > 80 << 20: continue
            for tag, needles in SIGNALS.items():
                for nd in needles:
                    if nd in data:
                        hits[tag][nd.decode('utf8','replace')].add(n)

def verdict(hits, inv):
    v = []
    if hits['proxy.vpn'] or (hits['proxy.raknet'] and hits['proxy.local']):
        v.append('PROXY  — intercepts/serves the bedrock protocol; version support = protocol support')
    if inv['packs'] or hits['pack.script'] or (hits['pack.files'] and hits['fs.gamedir']):
        v.append('PACK   — ships behaviour/resource packs into the game data dir (scripting API surface)')
    if inv['libs'] and (hits['native.inject'] or hits['root']):
        v.append('NATIVE — in-process work via .so (root/ptrace or repacked game)')
    if hits['fs.gamedir'] and not v:
        v.append('LAUNCHER — file management / starts the game, no runtime hook found')
    if hits['pack.hardening']:
        v.append('PACKED — dex is wrapped; dump from memory, static decompile will be thin')
    return v or ['UNKNOWN — none of the known shapes matched, read the dex by hand']

def have(tool): return shutil.which(tool) is not None

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('apks', nargs='+')
    ap.add_argument('-o', '--out', default='apk_triage_out')
    ap.add_argument('--jadx', action='store_true', help='also run jadx on every dex-carrying apk')
    a = ap.parse_args()

    os.makedirs(a.out, exist_ok=True)
    apks = expand(a.apks, a.out)

    hits = collections.defaultdict(lambda: collections.defaultdict(set))
    inv  = {'entries':0, 'libs':set(), 'packs':set(), 'js':set(), 'dex':set(),
            'pack_manifests':set(), 'perms':set(), 'mojang_refs':set()}

    print('== inputs ==')
    for p in apks:
        print(f'  {os.path.basename(p):50s} {os.path.getsize(p)/1e6:8.2f} MB  sha256:{sha(p)}')
        scan(p, hits, inv)

    print(f'\n== inventory ==\n  zip entries     {inv["entries"]}')
    print(f'  dex             {sorted(inv["dex"]) or "-"}')
    print(f'  native libs     {len(inv["libs"])}')
    for l in sorted(inv['libs'])[:40]: print(f'      {l}')
    print(f'  mc packs        {sorted(inv["packs"]) or "-"}')
    print(f'  asset js        {len(inv["js"])}')
    for l in sorted(inv['js'])[:20]: print(f'      {l}')
    print(f'  pack manifests  {len(inv["pack_manifests"])}')

    print('\n== manifest ==')
    for p in sorted(inv['perms']): print(f'  {p}')
    for p in sorted(inv['mojang_refs']): print(f'  ref {p}')

    print('\n== signals ==')
    for tag in SIGNALS:
        if tag not in hits: continue
        print(f'  [{tag}]')
        for needle, files in sorted(hits[tag].items()):
            where = ', '.join(sorted(files)[:3]) + (' …' if len(files) > 3 else '')
            print(f'      {needle:28s} {where}')

    print('\n== verdict ==')
    for line in verdict(hits, inv): print('  ' + line)

    print('\n== next ==')
    for p in apks:
        with zipfile.ZipFile(p) as z:
            dexed = any(re.search(r'classes\d*\.dex$', n) for n in z.namelist())
        if dexed:
            tgt = os.path.join(a.out, 'jadx_' + os.path.splitext(os.path.basename(p))[0])
            cmd = f'jadx -d {tgt} --no-res {p}'
            if a.jadx and have('jadx'):
                print('  running: ' + cmd)
                subprocess.run(cmd.split(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                print('  -> ' + tgt)
            else:
                print('  ' + cmd + ('' if have('jadx') else '    (jadx not installed)'))
    for l in sorted(inv['libs']):
        if l.endswith('.so'):
            print(f'  ghidraRun / ida64 on {l}   — start at exports + strings')

if __name__ == '__main__':
    main()
