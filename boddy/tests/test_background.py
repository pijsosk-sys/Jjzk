#!/usr/bin/env python3
"""Checks that Studio's Wine virtual desktop is black, not the classic Windows blue, on a PC with
x86-64 Wine, scrot and ImageMagick and an X display (e.g. Xvnc :1, with xrandr). Runs the launcher's
own functions on a fresh Wine prefix (start_windows, then set_window_mode as a launch does), then
opens a stand-in Windows program in the desktop and screenshots it with scrot while it starts, the
way Studio's desktop appears while Studio loads.
  --hard-kill  turns the launcher's "wineserver -k" into "-k9" (SIGKILL), which is what ending it under
               Box64 and proot on a phone amounts to: the wineserver never writes the registry it holds
               in memory to disk.
  --stale      starts from the state phones were left in: the desktop set up by an earlier launch, its
               background lost, and the launcher's v2 marker saying it was applied.
Usage: HOME=$(mktemp -d) DISPLAY=:1 python3 test_background.py /path/to/studio [--hard-kill] [--stale]
Exit code 0 when no screenshot shows blue above the desktop's taskbar."""
import os,sys,subprocess,time,tempfile
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from launcher_module import load
L=load(sys.argv[1])
if '--hard-kill' in sys.argv:
 _run=subprocess.run
 def run(args,*a,**k):
  if isinstance(args,list) and args and args[-1]=='-k' and 'wineserver' in ' '.join(args):args=[*args[:-1],'-k9']
  return _run(args,*a,**k)
 L.subprocess.run=run  # the launcher's calls only; this test's own stay as they are
wine=[subprocess.run(['which','wine'],capture_output=True,text=True).stdout.strip()]
L.FORCED.update(runtime='hangover')  # a one-element Wine command, like Hangover's; prefix_dir() is ROOT/prefix
L.ROOT.mkdir(parents=True,exist_ok=True);L.CACHE.mkdir(parents=True,exist_ok=True)
L.wine_command=lambda:wine
prefix=L.prefix_dir()
def clean_env():
 e=L.wine_env(prefix);e['WINEDEBUG']='-all';return e
def stop_wine():
 subprocess.run(['wineserver','-k'],env=clean_env(),stderr=subprocess.DEVNULL)
 time.sleep(1)
L.start_windows()
env=clean_env()
if '--stale' in sys.argv:
 # What a phone was left with: the virtual desktop saved by an earlier launch, Wine's blue background, v2 marker.
 subprocess.run(['wineserver','-w'],env=env,timeout=180)
 size=L.screen_size();want='%dx%d'%(size[0]-8,size[1]-72)
 for args in (['HKCU\\Software\\Wine\\Explorer','/v','Desktop','/d',L.DESKTOP_NAME],
              ['HKCU\\Software\\Wine\\Explorer\\Desktops','/v',L.DESKTOP_NAME,'/d',want],
              ['HKCU\\Control Panel\\Colors','/v','Background','/d','58 110 165']):
  subprocess.run([*wine,'reg','add',*args,'/t','REG_SZ','/f'],env=env,check=True,capture_output=True)
 subprocess.run(['wineserver','-w'],env=env,timeout=180)
 (prefix/'.boddy-window-mode').write_text('v2 '+want)
L.set_window_mode(wine,env,prefix)
q=subprocess.run([*wine,'reg','query','HKCU\\Control Panel\\Colors','/v','Background'],env=env,capture_output=True,text=True)
print('reg query:',(q.stdout.strip().splitlines() or ['(nothing)'])[-1].strip())
subprocess.run(['wineserver','-w'],env=env,timeout=180)
# The stand-in for Studio, started the way the launcher starts Studio (a fresh wineserver reads the settings).
proc=subprocess.Popen([*wine,'notepad'],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
shots=[];out=tempfile.mkdtemp(prefix='boddy-bg-')
for i in range(12):
 time.sleep(1.5);p=os.path.join(out,'shot%02d.png'%i)
 subprocess.run(['scrot','-o',p],check=True);shots.append(p)
def blue_pixels(p):
 # ImageMagick: pixels that are clearly blue (b well above r and g), as the Windows desktop colour is, above the
 # desktop's taskbar (its selected button is Windows' highlight colour, which is blue on purpose).
 r=subprocess.run(['convert',p,'-crop','+0-40','+repage','-fx','(b>0.35 && b>r+0.2 && b>g+0.1) ? 1 : 0',
  '-format','%[fx:mean*w*h]','info:'],capture_output=True,text=True)
 return int(float(r.stdout or 0))
counts=[blue_pixels(p) for p in shots]
desktop=subprocess.run(['xdotool','search','--name','RobloxStudio'],capture_output=True,text=True).stdout.split()
print('virtual desktop window open:',bool(desktop))
print('blue pixels per screenshot:',counts)
print('screenshots in',out)
stop_wine()
try:proc.wait(30)
except subprocess.TimeoutExpired:proc.kill()
ok=max(counts)==0 and bool(desktop)
print('PASS' if ok else 'FAIL');sys.exit(0 if ok else 1)
