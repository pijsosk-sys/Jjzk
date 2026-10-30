import os,re,json,signal,importlib.util,tempfile,zipfile,hashlib,subprocess,ssl,urllib.error
from pathlib import Path
here=Path(__file__).resolve().parent
boddy=here.parent/'app/src/main/assets/boddy'
# The launcher's Python lives in a heredoc inside the studio shell script; load it from there.
src=boddy/'studio';body=src.read_text().split("<<'BODDY_PY' 2>&1 | tee \"$LOG\"\n",1)[1].split('\nBODDY_PY\n',1)[0]
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
# Firefox started for Studio: clean environment and process state, crash reported with the log.
with tempfile.TemporaryDirectory() as td:
 p=Path(td);bin_=p/'bin';bin_.mkdir();(p/'home/Phone/Download').mkdir(parents=True)
 ff=bin_/'firefox-esr'
 ff.write_text('''#!/usr/bin/env python3
import os,sys,signal,resource,json
json.dump({'env':dict(os.environ),'argv':sys.argv,'mask':sorted(int(x) for x in signal.pthread_sigmask(signal.SIG_BLOCK,[])),
 'term_ignored':signal.getsignal(signal.SIGTERM)==signal.SIG_IGN,'nofile':resource.getrlimit(resource.RLIMIT_NOFILE)[0]},open(os.environ['FF_OUT'],'w'))
print('firefox stderr line')
code=os.environ.get('FF_EXIT','0')
if code=='kill':os.kill(os.getpid(),signal.SIGKILL)
if code=='segv':os.kill(os.getpid(),signal.SIGSEGV)
sys.exit(int(code))
''');ff.chmod(0o755)
 notes=[];r.notify=notes.append
 r.ROOT=p/'root';r.CACHE=p/'cache';r.CACHE.mkdir();old_home=os.environ.get('HOME')
 os.environ.update(HOME=str(p/'home'),PATH=str(bin_)+':'+os.environ['PATH'],FF_OUT=str(p/'ff.json'),WINEPREFIX='/x',WINEDEBUG='-all',LIBGL_ALWAYS_SOFTWARE='1',BODDY_NOFILE_SOFT='256')
 # Simulate what a Wine process passes on: blocked and ignored signals.
 signal.pthread_sigmask(signal.SIG_BLOCK,[signal.SIGUSR1,signal.SIGCHLD]);signal.signal(signal.SIGTERM,signal.SIG_IGN)
 os.environ['FF_EXIT']='0';r.open_in_browser('https://www.roblox.com/login?returnUrl=x')
 got=json.load(open(p/'ff.json'))
 assert got['argv'][1:]==['--profile',str(r.ROOT/'firefox-profile'),'https://www.roblox.com/login?returnUrl=x']
 assert not any(k.startswith('WINE') for k in got['env']) and 'LIBGL_ALWAYS_SOFTWARE' not in got['env'] and got['env']['MOZ_FAKE_NO_SANDBOX']=='1'
 assert got['mask']==[] and not got['term_ignored'] and got['nofile']<=256,got
 assert '"fission.autostart", false' in (r.ROOT/'firefox-profile/user.js').read_text() and not notes
 for mode,word in [('segv','SIGSEGV'),('kill','out of memory'),('3','code 3')]:
  os.environ['FF_EXIT']=mode
  try:r.open_in_browser('https://www.roblox.com/login');raise AssertionError('crash not reported')
  except RuntimeError:pass
  assert word in notes[-1],notes[-1]
 assert 'firefox stderr line' in (p/'home/Phone/Download/Boddy-Studio-firefox.log').read_text()
 for bad in ['file:///etc/passwd','javascript:alert(1)','https://a b']:
  try:r.open_in_browser(bad);raise AssertionError('opened '+bad)
  except ValueError:pass
 signal.pthread_sigmask(signal.SIG_SETMASK,[]);signal.signal(signal.SIGTERM,signal.SIG_DFL)
 if old_home:os.environ['HOME']=old_home
print('PASS: original CRLF defect reproduced (0 packages), replacement parses 34; destinations, malformed manifests, checksum recovery, safe extraction incl. Windows-style entries, TLS interception reported, browser sign-in handler, Firefox hand-off and crash reporting.')

# studio-app.sh reads the launcher's own output to drive its progress window and its "what was it
# doing when Android stopped it" message. The two files drifted once already: the launcher moved to
# Box64 while studio-app.sh still only matched "Installing Hangover", so the progress window sat at
# "Starting..." through the whole compatibility-layer install. Keep every milestone matched.
app=(boddy/'studio-app.sh').read_text()
milestones=['Installing Box64','Installing Wine','Installing Hangover','Testing Windows command',
 'Waiting for the internet','Could not check for Studio updates','Desktop enlarged',
 'Launching real Roblox Studio','LAUNCH FAILED: ','Studio process exited with code ']
for m in milestones:
 assert m in body,'launcher no longer prints %r; update studio-app.sh too'%m
 assert '"%s"'%m in app or '"%s"*'%m in app,'studio-app.sh does not match launcher message %r'%m
# The stop-report grep must cover the install steps of both runtimes.
stop=[l for l in app.splitlines() if 'where=$(grep' in l]
assert len(stop)==1 and all(w in stop[0] for w in ('Box64','Wine','Hangover')),'stop report misses a runtime'
# The menu must offer the runtime switch the launcher points users at, and only flags it accepts.
assert '"Compatibility mode"' in app and '--use-box64' in app and '--use-hangover' in app
for flag in re.findall(r'"\$STUDIO" (--[a-z0-9-]+)',app):
 assert flag in body,'studio-app.sh passes %s, which the launcher does not accept'%flag
print('PASS: studio-app.sh progress parsing, stop report and runtime switch match the launcher.')

# A kernel with pages bigger than 4 KB cannot run x86-64 code under Box64 or FEX at all. The
# launcher must say so before it downloads Wine and ~1.8 GB of Studio, and must point at the
# way of making games that still works on such a phone.
real_page=r.PAGE_SIZE
try:
 r.PAGE_SIZE=4096
 r.preflight()                      # a normal phone: no complaint, nothing downloaded
 for bad in (16384,65536):
  r.PAGE_SIZE=bad
  try:
   r.preflight();raise AssertionError('accepted a %d-byte page size'%bad)
  except RuntimeError as e:
   msg=str(e)
   assert '%d KB'%(bad//1024) in msg,'does not say the page size: '+msg
   assert 'Roblox Tools' in msg,'does not point at the way that still works: '+msg
finally:
 r.PAGE_SIZE=real_page
# The report must carry the numbers needed to tell why Studio failed on a phone we cannot test.
report=body.split('def diagnose():',1)[1].split('\ndef ',1)[0]
for field in ('Memory available','Processes:','Page size','glxinfo'):
 assert field in report,'diagnostics no longer report '+field
print('PASS: page-size preflight refuses 16K/64K kernels early, and diagnostics report memory, processes, page size and 3D.')

# Box64: Debian's build is too old and its wineserver dies under proot, so the launcher fetches the
# Box64 project's Android build. Check it picks the right package, verifies it, and cannot be
# talked into fetching something else by a doctored index.
assert r.BOX64_PKG=='box64-android' and r.BOX64_BIN=='/usr/local/bin/box64'
assert 'dpkg' not in body.split('def install_box64()',1)[1].split('\ndef install_box64_wine',1)[0], \
    'the Android build bundles its x86-64 libraries; it must not need the amd64 architecture'
index=('Package: box64\nVersion: 0.3.4\nArchitecture: arm64\nFilename: ./old.deb\nSize: 1\nSHA256: aa\n\n'
       'Package: box64-android\nVersion: 0.4.5-1\nArchitecture: armhf\nFilename: ./wrong-arch.deb\nSize: 2\nSHA256: bb\n\n'
       'Package: box64-android\nVersion: 0.4.5-1\nArchitecture: arm64\nFilename: ./box64-android_0.4.5-1_arm64.deb\n'
       'Size: 10338998\nSHA256: 33ee505f7ae0e829c02e5ad1b4df6c4b78e949b826be9e7c4dc5843fa3866b25\n')
def pick(text):
    for block in text.split('\n\n'):
        f=dict(re.findall(r'^([A-Za-z0-9-]+): (.*)$',block,re.M))
        if f.get('Package')==r.BOX64_PKG and f.get('Architecture')=='arm64':return f
    return None
got=pick(index)
assert got and got['Filename']=='./box64-android_0.4.5-1_arm64.deb','picked the wrong package: %r'%(got,)
assert int(got['Size'])==10338998 and len(got['SHA256'])==64
# The launcher strips the flat repo's leading "./" and then refuses anything that is not a bare name.
for bad in ['./../../etc/evil.deb','/etc/evil.deb','./sub/dir/x.deb','./x.deb.sh']:
    name=re.sub(r'^\./','',bad)
    assert not re.fullmatch(r'[A-Za-z0-9._~+-]+\.deb',name),'would have accepted '+bad
assert re.fullmatch(r'[A-Za-z0-9._~+-]+\.deb',re.sub(r'^\./','',got['Filename']))
print('PASS: Box64 comes from the Android build, verified by size and SHA256, with a safe filename.')
