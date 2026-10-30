#!/usr/bin/env python3
"""Studio Lite: a small Roblox game maker that runs on the phone's Linux desktop.

A local web server (127.0.0.1 only) for the page in web/. It keeps projects, builds them into a
Roblox place file (.rbxlx, which Roblox Studio and Open Cloud both read), checks scripts with Lune,
and publishes to a Roblox game with an Open Cloud API key.

    server.py [--port N] [--projects DIR] [--no-browser]

Every API call needs the token the server prints in its URL, so web pages open in the same
browser can't reach it.
"""
import argparse, copy, http.server, json, math, os, re, secrets, shutil, socketserver, subprocess, sys
import tempfile, threading, time, urllib.error, urllib.request, uuid
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).resolve().parent
WEB = HERE / 'web'
HOME = Path(os.environ.get('HOME', '/root'))
CONFIG = HOME / '.config' / 'studio-lite'
OPEN_CLOUD = os.environ.get('STUDIO_LITE_OPEN_CLOUD', 'https://apis.roblox.com')  # tests point this at a fake

# ---- Instances ---------------------------------------------------------------------------------

PART_CLASSES = ('Part', 'SpawnLocation')
SCRIPT_CLASSES = ('Script', 'LocalScript', 'ModuleScript')
CONTAINERS = ('Model', 'Folder')
ADDABLE = PART_CLASSES + SCRIPT_CLASSES + CONTAINERS

# Material enum values as Roblox stores them.
MATERIALS = {
    'Plastic': 256, 'SmoothPlastic': 272, 'Neon': 288, 'Wood': 512, 'WoodPlanks': 528, 'Marble': 784,
    'Basalt': 788, 'Slate': 800, 'CrackedLava': 804, 'Concrete': 816, 'Limestone': 820, 'Granite': 832,
    'Pavement': 836, 'Brick': 848, 'Pebble': 864, 'Cobblestone': 880, 'Rock': 896, 'Sandstone': 912,
    'CorrodedMetal': 1040, 'DiamondPlate': 1056, 'Foil': 1072, 'Metal': 1088, 'Grass': 1280,
    'LeafyGrass': 1284, 'Sand': 1296, 'Fabric': 1312, 'Snow': 1328, 'Mud': 1344, 'Ground': 1360,
    'Asphalt': 1376, 'Salt': 1392, 'Ice': 1536, 'Glacier': 1552, 'Glass': 1568, 'ForceField': 1584,
}
SHAPES = {'Ball': 0, 'Block': 1, 'Cylinder': 2}
SERVICES = ('Workspace', 'Lighting', 'ReplicatedStorage', 'ServerScriptService', 'ServerStorage',
            'StarterGui', 'StarterPack', 'StarterPlayer')


def part(name, position, size, color, material='Plastic', anchored=True, **extra):
    props = {'Position': list(position), 'Orientation': [0, 0, 0], 'Size': list(size), 'Color': list(color),
             'Material': material, 'Transparency': 0, 'Anchored': anchored, 'CanCollide': True, 'Shape': 'Block'}
    props.update(extra)
    return {'id': new_id(), 'name': name, 'className': 'Part', 'properties': props, 'children': []}


def new_id():
    return uuid.uuid4().hex[:12]


def node(name, class_name, children=(), **props):
    return {'id': new_id(), 'name': name, 'className': class_name, 'properties': props, 'children': list(children)}


def script(name, class_name, source):
    n = node(name, class_name)
    n['source'] = source
    return n


def template(name):
    base = part('Baseplate', (0, -4, 0), (512, 8, 512), (91, 154, 76), 'Grass')
    spawn = part('SpawnLocation', (0, 0.5, 0), (12, 1, 12), (163, 162, 165), 'SmoothPlastic')
    spawn['className'] = 'SpawnLocation'
    block = part('Block', (10, 2, 0), (4, 4, 4), (196, 40, 28))
    return {
        'name': name, 'universeId': '', 'placeId': '',
        'tree': [
            node('Workspace', 'Workspace', [base, spawn, block]),
            node('Lighting', 'Lighting'),
            node('ReplicatedStorage', 'ReplicatedStorage'),
            node('ServerScriptService', 'ServerScriptService', [script('Main', 'Script',
                '-- Runs on the server when the game starts.\n'
                'local block = workspace:WaitForChild("Block")\n\n'
                'while true do\n'
                '\tblock.Color = Color3.fromHSV(math.random(), 0.8, 1)\n'
                '\ttask.wait(1)\n'
                'end\n')]),
            node('ServerStorage', 'ServerStorage'),
            node('StarterGui', 'StarterGui'),
            node('StarterPack', 'StarterPack'),
            node('StarterPlayer', 'StarterPlayer', [node('StarterPlayerScripts', 'StarterPlayerScripts', [
                script('Hello', 'LocalScript', 'print("Hello from " .. game.Players.LocalPlayer.Name)\n')])]),
        ],
    }


# ---- Place file (.rbxlx) -----------------------------------------------------------------------

def num(v):
    v = float(v)
    return repr(int(v)) if v.is_integer() else repr(round(v, 6))


def rotation(orientation):
    """Rotation matrix rows for Roblox Orientation (degrees; applied Y, then X, then Z)."""
    x, y, z = (math.radians(float(a)) for a in orientation)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    mul = lambda a, b: [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mul(mul(ry, rx), rz)


def color_uint8(rgb):
    r, g, b = (max(0, min(255, int(round(float(c))))) for c in rgb)
    return (0xFF << 24) | (r << 16) | (g << 8) | b


def part_xml(props, class_name):
    out = []
    px, py, pz = (float(v) for v in props.get('Position', [0, 0, 0]))
    m = rotation(props.get('Orientation', [0, 0, 0]))
    out.append('<CoordinateFrame name="CFrame"><X>%s</X><Y>%s</Y><Z>%s</Z>%s</CoordinateFrame>' % (
        num(px), num(py), num(pz), ''.join('<R%d%d>%s</R%d%d>' % (i, j, num(m[i][j]), i, j) for i in range(3) for j in range(3))))
    sx, sy, sz = (max(0.001, float(v)) for v in props.get('Size', [4, 1, 2]))
    out.append('<Vector3 name="size"><X>%s</X><Y>%s</Y><Z>%s</Z></Vector3>' % (num(sx), num(sy), num(sz)))
    out.append('<Color3uint8 name="Color3uint8">%d</Color3uint8>' % color_uint8(props.get('Color', [163, 162, 165])))
    out.append('<token name="Material">%d</token>' % MATERIALS.get(props.get('Material'), 256))
    out.append('<float name="Transparency">%s</float>' % num(min(1, max(0, float(props.get('Transparency', 0))))))
    out.append('<bool name="Anchored">%s</bool>' % str(bool(props.get('Anchored', True))).lower())
    out.append('<bool name="CanCollide">%s</bool>' % str(bool(props.get('CanCollide', True))).lower())
    if class_name == 'Part':
        out.append('<token name="shape">%d</token>' % SHAPES.get(props.get('Shape'), 1))
    if class_name == 'SpawnLocation':
        out.append('<bool name="Neutral">true</bool>')
    return out


def cdata(text):
    return '<![CDATA[' + text.replace(']]>', ']]]]><![CDATA[>') + ']]>'


def item_xml(n, refs):
    ref = 'RBX%d' % len(refs)
    refs.append(ref)
    props = ['<string name="Name">%s</string>' % escape(n['name'])]
    if n['className'] in PART_CLASSES:
        props += part_xml(n.get('properties', {}), n['className'])
    if n['className'] in SCRIPT_CLASSES:
        props.append('<ProtectedString name="Source">%s</ProtectedString>' % cdata(n.get('source', '')))
    children = ''.join(item_xml(c, refs) for c in n.get('children', []))
    return '<Item class="%s" referent="%s"><Properties>%s</Properties>%s</Item>' % (n['className'], ref, ''.join(props), children)


def build_rbxlx(project):
    refs = []
    items = ''.join(item_xml(n, refs) for n in project['tree'])
    return ('<roblox xmlns:xmime="http://www.w3.org/2005/05/xmlmime" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
            'xsi:noNamespaceSchemaLocation="http://www.roblox.com/roblox.xsd" version="4">'
            '<External>null</External><External>nil</External>' + items + '</roblox>\n')


# ---- Checks ------------------------------------------------------------------------------------

def validate(project):
    """Raises ValueError unless the project is something this app wrote (names, classes, numbers)."""
    if not isinstance(project, dict) or not isinstance(project.get('tree'), list):
        raise ValueError('project has no tree')
    seen = set()
    def walk(n, depth):
        if depth > 64:
            raise ValueError('too deep')
        if not isinstance(n, dict) or not isinstance(n.get('name'), str) or not isinstance(n.get('className'), str):
            raise ValueError('bad instance')
        if not re.fullmatch(r'[A-Za-z][A-Za-z0-9]*', n['className']):
            raise ValueError('bad class name ' + n['className'])
        if n['className'] not in ADDABLE + SERVICES + ('StarterPlayerScripts', 'StarterCharacterScripts'):
            raise ValueError('unsupported class ' + n['className'])
        if not 0 < len(n['name']) <= 100:
            raise ValueError('names are 1 to 100 characters')
        if n.get('id') in seen:
            n['id'] = new_id()
        seen.add(n.setdefault('id', new_id()))
        for key in ('Position', 'Orientation', 'Size', 'Color'):
            v = n.get('properties', {}).get(key)
            if v is not None and (not isinstance(v, list) or len(v) != 3 or not all(isinstance(c, (int, float)) and math.isfinite(c) for c in v)):
                raise ValueError('%s of %s must be three numbers' % (key, n['name']))
        for c in n.get('children', []):
            walk(c, depth + 1)
    for n in project['tree']:
        walk(n, 0)


def find_lune():
    for p in (shutil.which('lune'), str(HOME / '.local' / 'bin' / 'lune')):
        if p and os.access(p, os.X_OK):
            return p
    return None


CHECK = HERE / 'check.luau'


def check_scripts(project):
    """Compiles every script with Lune. Returns {'available': bool, 'results': [...]}."""
    lune = find_lune()
    scripts = []
    def walk(n, path):
        here = path + [n['name']]
        if n['className'] in SCRIPT_CLASSES:
            scripts.append((n['id'], '.'.join(here), n.get('source', '')))
        for c in n.get('children', []):
            walk(c, here)
    for n in project['tree']:
        walk(n, [])
    if not lune:
        return {'available': False, 'results': []}
    results = []
    with tempfile.TemporaryDirectory() as tmp:
        files = []
        for i, (sid, path, source) in enumerate(scripts):
            f = Path(tmp) / ('%d.luau' % i)
            f.write_text(source)
            files.append(str(f))
        out = subprocess.run([lune, 'run', str(CHECK)] + files, capture_output=True, text=True, timeout=60) if files else None
        lines = out.stdout.splitlines() if out else []
        for i, (sid, path, source) in enumerate(scripts):
            line = next((l for l in lines if l.startswith('%d\t' % i)), None)
            if line is None:
                results.append({'id': sid, 'path': path, 'ok': False, 'error': 'checker gave no answer: ' + (out.stderr.strip()[-300:] if out else '')})
            else:
                _, status, msg = (line.split('\t', 2) + [''])[:3]
                results.append({'id': sid, 'path': path, 'ok': status == 'ok', 'error': msg.replace('\\n', '\n')})
    return {'available': True, 'results': results}


# ---- Publishing --------------------------------------------------------------------------------

def publish(project, api_key):
    universe, place = str(project.get('universeId', '')).strip(), str(project.get('placeId', '')).strip()
    if not universe.isdigit() or not place.isdigit():
        raise ValueError('Set the Universe ID and Place ID in Game settings first.')
    body = build_rbxlx(project).encode()
    req = urllib.request.Request(
        '%s/universes/v1/%s/places/%s/versions?versionType=Published' % (OPEN_CLOUD, universe, place),
        data=body, method='POST', headers={'x-api-key': api_key, 'Content-Type': 'application/xml'})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return json.loads(r.read() or b'{}')
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors='replace')[:400]
        hint = {401: 'The API key is wrong or expired.',
                403: 'The API key may not publish this game: give it the universe-places write permission for this experience.',
                404: 'No game with that Universe ID and Place ID.'}.get(e.code, '')
        raise RuntimeError('Roblox said %d. %s %s' % (e.code, hint, detail))


# ---- Storage -----------------------------------------------------------------------------------

class Store:
    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self.lock = threading.Lock()

    def path(self, name):
        if not re.fullmatch(r'[A-Za-z0-9 _-]{1,40}', name or '') or name.strip() != name:
            raise ValueError('Project names use letters, numbers, spaces, - and _ (up to 40).')
        return self.root / name / 'project.json'

    def list(self):
        return sorted(p.parent.name for p in self.root.glob('*/project.json'))

    def load(self, name):
        return json.loads(self.path(name).read_text())

    def save(self, name, project):
        validate(project)
        p = self.path(name)
        p.parent.mkdir(parents=True, exist_ok=True)
        with self.lock:
            # Keep the previous save: a bad save can be undone by hand.
            if p.exists():
                shutil.copyfile(p, p.with_suffix('.json.bak'))
            tmp = p.with_suffix('.tmp')
            tmp.write_text(json.dumps(project, indent=1))
            tmp.replace(p)

    def create(self, name):
        p = self.path(name)
        if p.exists():
            raise ValueError('A project called %s already exists.' % name)
        self.save(name, template(name))
        return self.load(name)


def load_key():
    try:
        return (CONFIG / 'api-key').read_text().strip()
    except OSError:
        return ''


def save_key(key):
    CONFIG.mkdir(parents=True, exist_ok=True)
    f = CONFIG / 'api-key'
    f.touch(mode=0o600)
    os.chmod(f, 0o600)
    f.write_text(key.strip())


def downloads():
    for d in (HOME / 'Phone' / 'Download', HOME / 'Downloads'):
        if d.is_dir():
            return d
    d = HOME / 'Downloads'
    d.mkdir(parents=True, exist_ok=True)
    return d


# ---- HTTP --------------------------------------------------------------------------------------

TYPES = {'.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
         '.svg': 'image/svg+xml', '.png': 'image/png'}


def make_handler(store, token):
    class Handler(http.server.BaseHTTPRequestHandler):
        server_version = 'StudioLite'

        def log_message(self, fmt, *args):
            pass

        def send(self, code, body, ctype='application/json'):
            data = body if isinstance(body, bytes) else json.dumps(body).encode()
            self.send_response(code)
            self.send_header('Content-Type', ctype)
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            path = self.path.split('?', 1)[0]
            if path == '/':
                path = '/index.html'
            f = (WEB / path.lstrip('/')).resolve()
            if WEB.resolve() not in f.parents or not f.is_file() or f.suffix not in TYPES:
                return self.send(404, {'error': 'not found'})
            self.send(200, f.read_bytes(), TYPES[f.suffix])

        def do_POST(self):
            if not secrets.compare_digest(self.headers.get('X-Token', ''), token):
                return self.send(403, {'error': 'bad token'})
            try:
                size = int(self.headers.get('Content-Length', 0))
                if size > 20_000_000:
                    raise ValueError('too large')
                req = json.loads(self.rfile.read(size) or b'{}')
                self.send(200, self.route(self.path, req))
            except (ValueError, KeyError, RuntimeError, OSError) as e:
                self.send(400, {'error': str(e)})

        def route(self, path, req):
            if path == '/api/list':
                return {'projects': store.list(), 'lune': bool(find_lune()), 'hasKey': bool(load_key()),
                        'materials': list(MATERIALS), 'shapes': list(SHAPES), 'addable': list(ADDABLE)}
            if path == '/api/create':
                return store.create(req['name'])
            if path == '/api/load':
                return store.load(req['name'])
            if path == '/api/save':
                store.save(req['name'], req['project'])
                return {'ok': True}
            if path == '/api/check':
                return check_scripts(req['project'])
            if path == '/api/export':
                validate(req['project'])
                out = downloads() / (re.sub(r'[^A-Za-z0-9_-]', '_', req['project'].get('name', 'game')) + '.rbxlx')
                out.write_text(build_rbxlx(req['project']))
                return {'path': str(out)}
            if path == '/api/key':
                save_key(req.get('key', ''))
                return {'hasKey': bool(load_key())}
            if path == '/api/publish':
                validate(req['project'])
                if not all(str(req['project'].get(k, '')).strip().isdigit() for k in ('universeId', 'placeId')):
                    raise ValueError('Set the Universe ID and Place ID in Game settings first.')
                key = load_key()
                if not key:
                    raise ValueError('Add your Open Cloud API key in Game settings first.')
                return publish(req['project'], key)
            if path == '/api/play':
                place = str(req.get('placeId', ''))
                if not place.isdigit():
                    raise ValueError('Set the Place ID first.')
                opener = next((p for p in ('/opt/boddy/open-on-phone', shutil.which('xdg-open')) if p and os.access(p, os.X_OK)), None)
                if not opener:
                    raise ValueError('No way to open links here.')
                subprocess.Popen([opener, 'https://www.roblox.com/games/' + place], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                return {'ok': True}
            if path == '/api/install-lune':
                tool = shutil.which('roblox-setup')
                if not tool:
                    raise ValueError('roblox-setup is not installed.')
                r = subprocess.run([tool], capture_output=True, text=True, timeout=600)
                return {'lune': bool(find_lune()), 'log': (r.stdout + r.stderr)[-1500:]}
            raise ValueError('unknown call ' + path)

    return Handler


class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--port', type=int, default=0)
    ap.add_argument('--projects', default=str(HOME / 'Projects' / 'Studio Lite'))
    ap.add_argument('--token', default=None)
    a = ap.parse_args()
    token = a.token or secrets.token_urlsafe(18)
    srv = Server(('127.0.0.1', a.port), make_handler(Store(a.projects), token))
    print('http://127.0.0.1:%d/#%s' % (srv.server_address[1], token), flush=True)
    srv.serve_forever()


if __name__ == '__main__':
    main()
