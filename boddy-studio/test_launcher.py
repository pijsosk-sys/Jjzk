import importlib.util,tempfile,zipfile,hashlib,subprocess,ssl,urllib.error
from pathlib import Path
here=Path(__file__).resolve().parent
# The launcher's Python lives in a heredoc inside the studio shell script; load it from there.
src=here/'studio';body=src.read_text().split("<<'BODDY_PY' 2>&1 | tee \"$LOG\"\n",1)[1].split('\nBODDY_PY\n',1)[0]
with tempfile.NamedTemporaryFile('w',suffix='.py',delete=False) as f:f.write(body)
spec=importlib.util.spec_from_file_location('runtime',f.name);r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
raw=(here/'manifest.txt').read_bytes();pkgs=r.manifest(raw)
old=subprocess.run(['bash','-c',"grep -E '\\.zip$' manifest.txt | tr -d '\\r'"],capture_output=True,cwd=here).stdout
assert not old and len(pkgs)==34
assert r.destination('extracontent-luapackages.zip')=='ExtraContent/LuaPackages'
assert r.destination('content-textures3.zip')=='PlatformContent/pc/textures'
for bad in [b'v0\n../evil.zip\n' + b'0'*32+b'\n1\n1',b'v0\nx.zip\nBAD\n1\n1',b'v0\nx.zip\n'+b'0'*32+b'\n-1\n1']:
 try:r.manifest(bad);raise AssertionError('accepted malformed manifest')
 except ValueError:pass
with tempfile.TemporaryDirectory() as td:
 p=Path(td);data=b'checked test package';src=p/'src';src.write_bytes(data);out=p/'out'
 r.download(src.as_uri(),out,len(data),hashlib.md5(data).hexdigest());assert out.read_bytes()==data
 out.write_bytes(b'bad');r.download(src.as_uri(),out,len(data),hashlib.md5(data).hexdigest());assert out.read_bytes()==data
 z=p/'test.zip'
 with zipfile.ZipFile(z,'w') as f:f.writestr('../escape','bad')
 try:r.extract_zip(z,p/'dest');raise AssertionError('accepted ZIP traversal')
 except ValueError:pass
 with zipfile.ZipFile(z,'w') as f:f.writestr('folder/file','good')
 r.extract_zip(z,p/'dest');assert (p/'dest/folder/file').read_text()=='good'
 # Real Roblox packages: Windows separators, a bare root entry and leading-separator directory entries.
 with zipfile.ZipFile(z,'w') as f:
  f.writestr('\\','');f.writestr('\\Lua\\','');f.writestr('\\Lua\\Dark\\','');f.writestr('Lua\\Dark\\icon.png','png')
 r.extract_zip(z,p/'win');assert (p/'win/Lua/Dark/icon.png').read_text()=='png' and (p/'win/Lua').is_dir()
 for bad in ['\\..\\escape','C:\\evil','\\a\\..\\..\\b']:
  with zipfile.ZipFile(z,'w') as f:f.writestr(bad,'bad')
  try:r.extract_zip(z,p/'win2');raise AssertionError('accepted '+bad)
  except ValueError:pass
assert r.destination('studiocontent-textures.zip')=='StudioContent/textures'
def intercepted(*a,**k):raise urllib.error.URLError(ssl.SSLCertVerificationError(1,'self-signed certificate in certificate chain'))
real=r.urllib.request.urlopen;r.urllib.request.urlopen=intercepted;r.presented_chain=lambda host,port=443:[]
try:r.fetch(r.VERSION_URL);raise AssertionError('certificate failure not reported')
except r.TlsError as e:assert 'clientsettingscdn.roblox.com' in str(e) and 'update-ca-certificates' in str(e)
finally:r.urllib.request.urlopen=real
print('PASS: original CRLF defect reproduced (0 packages), replacement parses 34; destinations, malformed manifests, checksum recovery, safe extraction incl. Windows-style entries, TLS interception reported.')
