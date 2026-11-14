#!/usr/bin/env python3
"""Download Minecraft 1.21.11 (client, libraries, natives, assets), Fabric loader + API,
and produce client-intermediary.jar. Everything lands in .cache/ (git-ignored)."""
import json, os, sys, urllib.request, subprocess, zipfile, concurrent.futures as cf
MC = "1.21.11"; LOADER = "0.18.4"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
C = os.path.join(ROOT, ".cache")

def get(url, path=None):
    if path and os.path.exists(path) and os.path.getsize(path) > 0: return path
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "mc-setup"})
            data = urllib.request.urlopen(req, timeout=120).read()
            break
        except Exception as e:
            if attempt == 4: raise RuntimeError(f"{url}: {e}")
    if not path: return data
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path + ".tmp", "wb") as f: f.write(data)
    os.replace(path + ".tmp", path); return path

def maven(repo, coord):
    g, a, v = coord.split(":")[:3]
    cl = coord.split(":")[3] if coord.count(":") > 2 else None
    name = f"{a}-{v}" + (f"-{cl}" if cl else "") + ".jar"
    return f"{repo.rstrip('/')}/{g.replace('.', '/')}/{a}/{v}/{name}", name

def many(jobs):
    with cf.ThreadPoolExecutor(16) as ex:
        list(ex.map(lambda j: get(*j), jobs))

man = json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
vurl = next(v["url"] for v in man["versions"] if v["id"] == MC)
ver = json.loads(get(vurl))
get(ver["downloads"]["client"]["url"], f"{C}/client.jar")
jobs = []
for lib in ver["libraries"]:
    rules = lib.get("rules")
    if rules:
        ok = False
        for r in rules:
            osn = r.get("os", {}).get("name")
            if osn is None or osn == "linux": ok = r["action"] == "allow"
        if not ok: continue
    art = lib.get("downloads", {}).get("artifact")
    if not art: continue
    if "natives-" in lib["name"] and not lib["name"].endswith("natives-linux"): continue
    jobs.append((art["url"], f"{C}/libs/{os.path.basename(art['path'])}"))
many(jobs)

# Fabric launch profile
prof = json.loads(get(f"https://meta.fabricmc.net/v2/versions/loader/{MC}/{LOADER}/profile/json"))
jobs = []
for lib in prof["libraries"]:
    url, name = maven(lib["url"], lib["name"])
    jobs.append((url, f"{C}/fabric/{name}"))
many(jobs)
with open(f"{C}/fabric-main.txt", "w") as f: f.write(prof["mainClass"] if isinstance(prof["mainClass"], str) else prof["mainClass"]["client"])

# tools
F = "https://maven.fabricmc.net"; M = "https://repo1.maven.org/maven2"
tools = [(F, "net.fabricmc:tiny-remapper:0.11.1:fat"), (M, "org.vineflower:vineflower:1.11.1")]
for repo, c in tools:
    url, name = maven(repo, c); get(url, f"{C}/tools/{name}")
url, name = maven(F, f"net.fabricmc:intermediary:{MC}:v2"); get(url, f"{C}/tools/intermediary.jar")
with zipfile.ZipFile(f"{C}/tools/intermediary.jar") as z:
    open(f"{C}/tools/intermediary.tiny", "wb").write(z.read("mappings/mappings.tiny"))

# Fabric API (from modrinth)
vs = json.loads(get('https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22' + MC + '%22%5D&loaders=%5B%22fabric%22%5D'))
pick = next((v for v in vs if v["version_number"].startswith("0.141.4")), vs[0])
get(pick["files"][0]["url"], f"{C}/mods/fabric-api.jar")

# assets (skip sounds)
idx = ver["assetIndex"]
ipath = f"{C}/assets/indexes/{idx['id']}.json"; get(idx["url"], ipath)
with open(f"{C}/asset-index.txt", "w") as f: f.write(idx["id"])
objs = json.load(open(ipath))["objects"]
jobs = []
for name, o in objs.items():
    if name.endswith(".ogg"): continue
    h = o["hash"]
    jobs.append((f"https://resources.download.minecraft.net/{h[:2]}/{h}", f"{C}/assets/objects/{h[:2]}/{h}"))
many(jobs)

# intermediary-named client
out = f"{C}/client-intermediary.jar"
if not os.path.exists(out):
    subprocess.check_call(["java", "-jar", f"{C}/tools/tiny-remapper-0.11.1-fat.jar", f"{C}/client.jar", out,
                           f"{C}/tools/intermediary.tiny", "official", "intermediary"] + [os.path.join(f"{C}/libs", x) for x in os.listdir(f"{C}/libs")])
print("setup done")
