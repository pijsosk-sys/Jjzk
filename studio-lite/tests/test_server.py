"""Tests for Studio Lite's server. Lune (LUNE=/path/to/lune, or on PATH) reads the place files back.

    python3 -m unittest discover -s tests -v
"""
import http.server, json, os, shutil, subprocess, sys, tempfile, threading, unittest, urllib.error, urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import server  # noqa: E402

LUNE = os.environ.get('LUNE') or shutil.which('lune')


def read_back(project):
    """The place file as Roblox's own format reader (Lune) sees it: {full name: properties}."""
    with tempfile.TemporaryDirectory() as tmp:
        f = Path(tmp) / 'game.rbxlx'
        f.write_text(server.build_rbxlx(project))
        out = subprocess.run([LUNE, 'run', str(HERE / 'dump.luau'), str(f)], capture_output=True, text=True, timeout=60)
        if out.returncode:
            raise AssertionError(out.stderr)
        return {row['path']: row for row in map(json.loads, out.stdout.splitlines())}


@unittest.skipUnless(LUNE, 'needs lune')
class PlaceFile(unittest.TestCase):
    def test_template_reads_back(self):
        got = read_back(server.template('Test'))
        base = got['Workspace.Baseplate']
        self.assertEqual(base['class'], 'Part')
        self.assertEqual(base['size'], [512, 8, 512])
        self.assertEqual(base['position'], [0, -4, 0])
        self.assertEqual(base['color'], [91, 154, 76])
        self.assertEqual(base['material'], 'Grass')
        self.assertTrue(base['anchored'])
        self.assertEqual(got['Workspace.SpawnLocation']['class'], 'SpawnLocation')
        self.assertIn('Color3.fromHSV', got['ServerScriptService.Main']['source'])
        self.assertEqual(got['StarterPlayer.StarterPlayerScripts.Hello']['class'], 'LocalScript')

    def test_every_material_and_shape(self):
        p = server.template('M')
        ws = p['tree'][0]
        ws['children'] = []
        for i, m in enumerate(server.MATERIALS):
            ws['children'].append(server.part('M%d' % i, (i * 5, 1, 0), (2, 2, 2), (10, 20, 30), m))
        for s in server.SHAPES:
            ws['children'].append(server.part('S' + s, (0, 1, 9), (3, 3, 3), (1, 2, 3), Shape=s))
        got = read_back(p)
        for i, m in enumerate(server.MATERIALS):
            self.assertEqual(got['Workspace.M%d' % i]['material'], m)
        for s in server.SHAPES:
            self.assertEqual(got['Workspace.S' + s]['shape'], s)

    def test_orientation_and_properties(self):
        p = server.template('O')
        n = server.part('Tilted', (1.5, 2.25, -3), (4, 1.5, 2), (255, 0, 128), 'Neon', anchored=False,
                        Orientation=[30, 45, -10], Transparency=0.4, CanCollide=False)
        p['tree'][0]['children'] = [n]
        row = read_back(p)['Workspace.Tilted']
        for a, b in zip(row['orientation'], [30, 45, -10]):
            self.assertAlmostEqual(a, b, places=3)
        self.assertEqual(row['position'], [1.5, 2.25, -3])
        self.assertEqual(row['color'], [255, 0, 128])
        self.assertAlmostEqual(row['transparency'], 0.4, places=5)
        self.assertFalse(row['anchored'])
        self.assertFalse(row['canCollide'])

    def test_awkward_names_and_source(self):
        p = server.template('N')
        src = 'print("]]> <tag> & done")\nlocal s = [[x]]\n'
        p['tree'][3]['children'] = [server.script('A & <B> "c"', 'Script', src)]
        got = read_back(p)
        self.assertEqual(got['ServerScriptService.A & <B> "c"']['source'], src)

    def test_nested_models(self):
        p = server.template('Nest')
        inner = server.part('Leg', (0, 1, 0), (1, 2, 1), (0, 0, 0))
        p['tree'][0]['children'] = [server.node('Chair', 'Model', [server.node('Parts', 'Folder', [inner])])]
        self.assertEqual(read_back(p)['Workspace.Chair.Parts.Leg']['size'], [1, 2, 1])


@unittest.skipUnless(LUNE, 'needs lune')
class Scripts(unittest.TestCase):
    def setUp(self):
        os.environ['PATH'] = str(Path(LUNE).parent) + os.pathsep + os.environ['PATH']

    def test_good_and_bad(self):
        p = server.template('C')
        p['tree'][3]['children'] = [
            server.script('Good', 'Script', 'local x: number = 1\nx += 1\nprint(`x is {x}`)\ncontinue_ = if x > 1 then "a" else "b"\n'),
            server.script('Bad', 'Script', 'local x = \nif then end\n'),
        ]
        r = server.check_scripts(p)
        self.assertTrue(r['available'])
        res = {x['path']: x for x in r['results']}
        self.assertTrue(res['ServerScriptService.Good']['ok'], res['ServerScriptService.Good'])
        self.assertFalse(res['ServerScriptService.Bad']['ok'])
        self.assertTrue(res['ServerScriptService.Bad']['error'].startswith('line 2: '), res['ServerScriptService.Bad'])
        self.assertTrue(res['StarterPlayer.StarterPlayerScripts.Hello']['ok'])


class Validation(unittest.TestCase):
    def test_rejects_unknown_classes_and_bad_vectors(self):
        p = server.template('V')
        p['tree'][0]['children'].append(server.node('X', 'RemoteEvent'))
        with self.assertRaises(ValueError):
            server.validate(p)
        p = server.template('V')
        p['tree'][0]['children'][0]['properties']['Size'] = [1, 'a', 3]
        with self.assertRaises(ValueError):
            server.validate(p)
        p = server.template('V')
        p['tree'][0]['children'][0]['properties']['Position'] = [1, float('nan'), 3]
        with self.assertRaises(ValueError):
            server.validate(p)

    def test_project_names(self):
        store = server.Store(tempfile.mkdtemp())
        for bad in ('../x', 'a/b', '', ' lead', 'x' * 41, '.hidden'):
            with self.assertRaises(ValueError, msg=bad):
                store.path(bad)
        store.path('My Game 2')


class FakeOpenCloud(http.server.BaseHTTPRequestHandler):
    seen = []

    def log_message(self, *a):
        pass

    def do_POST(self):
        body = self.rfile.read(int(self.headers['Content-Length']))
        FakeOpenCloud.seen.append((self.path, dict(self.headers), body))
        if self.headers.get('x-api-key') != 'good-key':
            self.send_response(401); self.end_headers(); self.wfile.write(b'{"message":"Invalid API Key"}'); return
        data = b'{"versionNumber": 7}'
        self.send_response(200); self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data)


class Http(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.home = tempfile.mkdtemp()
        server.CONFIG = Path(cls.home) / 'config'
        server.HOME = Path(cls.home)
        cls.srv = server.Server(('127.0.0.1', 0), server.make_handler(server.Store(Path(cls.home) / 'projects'), 'tok'))
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()
        cls.base = 'http://127.0.0.1:%d' % cls.srv.server_address[1]
        cls.fake = http.server.HTTPServer(('127.0.0.1', 0), FakeOpenCloud)
        threading.Thread(target=cls.fake.serve_forever, daemon=True).start()
        server.OPEN_CLOUD = 'http://127.0.0.1:%d' % cls.fake.server_address[1]

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown(); cls.fake.shutdown()

    def call(self, path, body=None, token='tok'):
        req = urllib.request.Request(self.base + '/api/' + path, data=json.dumps(body or {}).encode(), method='POST',
                                     headers={'X-Token': token, 'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read())

    def test_token_required(self):
        self.assertEqual(self.call('list', token='wrong')[0], 403)
        self.assertEqual(self.call('list', token='')[0], 403)

    def test_static_files_stay_in_web(self):
        with urllib.request.urlopen(self.base + '/') as r:
            self.assertIn(b'Studio Lite', r.read())
        for bad in ('/../server.py', '/%2e%2e/server.py', '/vendor/../../server.py'):
            with self.assertRaises(urllib.error.HTTPError, msg=bad):
                urllib.request.urlopen(self.base + bad)

    def test_create_save_load_export(self):
        code, p = self.call('create', {'name': 'Round Trip'})
        self.assertEqual(code, 200)
        self.assertEqual(self.call('create', {'name': 'Round Trip'})[0], 400)
        p['tree'][0]['children'].append(server.part('New', (1, 2, 3), (1, 1, 1), (9, 9, 9)))
        self.assertEqual(self.call('save', {'name': 'Round Trip', 'project': p})[0], 200)
        code, back = self.call('load', {'name': 'Round Trip'})
        self.assertEqual(back['tree'][0]['children'][-1]['name'], 'New')
        self.assertIn('Round Trip', self.call('list')[1]['projects'])
        (Path(self.home) / 'Downloads').mkdir(exist_ok=True)
        code, r = self.call('export', {'project': back})
        self.assertEqual(code, 200, r)
        self.assertTrue(Path(r['path']).read_text().startswith('<roblox'))

    def test_publish(self):
        p = server.template('Pub')
        self.assertIn('Universe ID', self.call('publish', {'project': p})[1]['error'])
        p['universeId'], p['placeId'] = '123', '456'
        self.assertIn('API key', self.call('publish', {'project': p})[1]['error'])
        self.call('key', {'key': 'wrong-key'})
        code, r = self.call('publish', {'project': p})
        self.assertEqual(code, 400)
        self.assertIn('401', r['error'])
        self.call('key', {'key': 'good-key\n'})
        self.assertEqual(oct((server.CONFIG / 'api-key').stat().st_mode & 0o777), '0o600')
        code, r = self.call('publish', {'project': p})
        self.assertEqual((code, r), (200, {'versionNumber': 7}))
        path, headers, body = FakeOpenCloud.seen[-1]
        self.assertEqual(path, '/universes/v1/123/places/456/versions?versionType=Published')
        self.assertEqual(headers['Content-Type'], 'application/xml')
        self.assertTrue(body.startswith(b'<roblox'))


if __name__ == '__main__':
    unittest.main()
