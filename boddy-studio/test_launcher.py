import os,importlib.util,tempfile,zipfile,hashlib,subprocess,ssl,urllib.error
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
# Browser sign-in: handler registration keeps other defaults, Wine browser set once, links validated.
with tempfile.TemporaryDirectory() as td:
 p=Path(td);ran=[];r.command=lambda args,*a,**k:ran.append(args)
 os.environ['XDG_CONFIG_HOME']=str(p/'config');os.environ['XDG_DATA_HOME']=str(p/'data')
 (p/'config').mkdir();(p/'config/mimeapps.list').write_text('[Default Applications]\ntext/html=firefox-esr.desktop;\n')
 (p/'prefix').mkdir();r.setup_browser_login('wine',{},p/'prefix');r.setup_browser_login('wine',{},p/'prefix')
 m=(p/'config/mimeapps.list').read_text()
 assert 'text/html=firefox-esr.desktop;' in m and 'x-scheme-handler/roblox-studio-auth=boddy-roblox-studio-auth.desktop;' in m
 assert 'MimeType=x-scheme-handler/roblox-studio-auth;' in (p/'data/applications'/r.AUTH_DESKTOP).read_text()
 assert len(ran)==1 and 'firefox-esr' in ran[0][-2]
 for bad in ['roblox-studio-auth:','roblox-studio-auth:a b','roblox-studio-auth:x\n--evil','https://x']:
  try:r.pass_auth(bad);raise AssertionError('accepted '+repr(bad))
  except ValueError:pass
print('PASS: original CRLF defect reproduced (0 packages), replacement parses 34; destinations, malformed manifests, checksum recovery, safe extraction incl. Windows-style entries, TLS interception reported, browser sign-in handler.')
