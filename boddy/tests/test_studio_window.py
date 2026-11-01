#!/usr/bin/env python3
"""On an XFCE desktop (xfwm4, xfce4-panel, xfdesktop) on Xvnc with x86-64 Wine, checks the launcher's window
handling while Studio runs:
  - Studio's Wine desktop window covering the taskbar is moved back above it (keep_studio_above_taskbar),
  - the sign-in browser that opened behind Studio is brought in front of it (bring_to_front),
  - the desktop behind Studio is closed and started again (pause_desktop / resume_desktop).
A Wine notepad in the RobloxStudio desktop stands in for Studio, a zenity window for Firefox.
Usage: HOME=$(mktemp -d) DISPLAY=:1 dbus-launch python3 test_studio_window.py /path/to/studio"""
import os,sys,subprocess,time,threading
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from launcher_module import load
L=load(sys.argv[1])
fails=0
def check(name,ok):
 global fails
 print(('ok   ' if ok else 'FAIL ')+name);fails+=0 if ok else 1
def state(pid):
 return open('/proc/%d/stat'%pid).read().rsplit(')',1)[1].split()[0]
def geometry(wid):
 g=dict(l.split('=') for l in L.xdo('getwindowgeometry','--shell',wid).split())
 return int(g['X']),int(g['Y']),int(g['WIDTH']),int(g['HEIGHT'])
L.ROOT.mkdir(parents=True,exist_ok=True);L.CACHE.mkdir(parents=True,exist_ok=True)
quiet=dict(stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
wm=subprocess.Popen(['xfwm4','--replace'],**quiet)
panel=subprocess.Popen(['xfce4-panel'],**quiet)
desk=subprocess.Popen(['xfdesktop'],**quiet)
for _ in range(50):
 area=L.work_area()
 if area and (area[1]>0 or area[3]<L.screen_size()[1]):break  # the panel has reserved its space
 time.sleep(0.3)
print('screen',L.screen_size(),'work area',area)
check('the taskbar reserves part of the screen',bool(area) and (area[1]>0 or area[3]<L.screen_size()[1]))

wine=[subprocess.run(['which','wine'],capture_output=True,text=True).stdout.strip()]
L.FORCED.update(runtime='hangover');L.wine_command=lambda:wine
prefix=L.prefix_dir();L.start_windows();env=L.wine_env(prefix);env['WINEDEBUG']='-all'
ax,ay,aw,ah=area
def open_studio():
 p=subprocess.Popen([*wine,'notepad'],env=env,**quiet)
 for _ in range(60):
  ids=L.xdo('search','--onlyvisible','--name','^%s - '%L.DESKTOP_NAME).split()
  if ids:time.sleep(2);return p,ids[0]
  time.sleep(1)
 return p,None
# As a launch sets it up now: the desktop sized from the work area fits above the taskbar from the start.
L.set_window_mode(wine,env,prefix)
said=[];L.say=lambda s:(said.append(s),print('  launcher:',s))
stop=threading.Event();L.keep_studio_above_taskbar(stop)  # a launch runs this while Studio runs
studio,wid=open_studio()
check('Studio stand-in window is open',bool(wid))
time.sleep(8)
x,y,ww,hh=geometry(wid);print('launch, desktop sized from the work area:',(x,y,ww,hh))
check('launch: Studio\'s window leaves the taskbar free',y>=ay and y+hh<=ay+ah)
stop.set();subprocess.run(['wineserver','-k'],env=env,**quiet);studio.wait()
# Studio's desktop as big as the whole screen: it covers the taskbar, as on the phone before.
w,h=L.screen_size()
L.apply_desktop_settings(wine,env,prefix,'%dx%d'%(w,h))
studio,wid=open_studio()
x,y,ww,hh=geometry(wid)
print('before:',(x,y,ww,hh))
check('before: it covers the taskbar (the problem)',y<ay or y+hh>ay+ah)
said=[];L.say=lambda s:(said.append(s),print('  launcher:',s))
stop=threading.Event();L.keep_studio_above_taskbar(stop)
time.sleep(8)
x,y,ww,hh=geometry(wid);print('after:',(x,y,ww,hh))
check('after: inside the work area, taskbar free',y>=ay and y+hh<=ay+ah)

# The sign-in browser opens behind Studio's window.
L.xdo('windowactivate',wid);time.sleep(1)
browser=subprocess.Popen(['zenity','--info','--title=Sign in','--text=Roblox sign-in stand-in'],**quiet)
time.sleep(2);L.xdo('windowactivate',wid);time.sleep(1)  # Studio takes the front again, as it did on the phone
L.bring_to_front(browser,30)
active=L.xdo('getactivewindow').strip()
check('the sign-in browser is the active window',active in L.xdo('search','--pid',str(browser.pid)).split())
L.xdo('windowactivate',wid);time.sleep(1)
stack=subprocess.run(['xprop','-root','_NET_CLIENT_LIST_STACKING'],capture_output=True,text=True).stdout
order=[int(v,16) for v in stack.split('#')[-1].replace(',',' ').split()]
bwin=[int(v) for v in L.xdo('search','--onlyvisible','--pid',str(browser.pid)).split() if int(v) in order]
swin=[int(v) for v in L.xdo('search','--onlyvisible','--name','^%s - '%L.DESKTOP_NAME).split() if int(v) in order]
check('it stays above Studio when Studio is tapped',bool(bwin) and bool(swin) and order.index(bwin[0])>order.index(swin[0]))
browser.kill()

# The desktop behind Studio is closed, then started again.
def running(name):
 """Whether a live process (not an ended one waiting to be reaped) has this name."""
 for n in os.listdir('/proc'):
  try:
   if n.isdigit() and open('/proc/%s/comm'%n).read().strip()==name and state(int(n))!='Z':return True
  except OSError:pass
 return False
resume=L.pause_desktop();time.sleep(2)
check('xfdesktop closed while Studio runs (a process slot freed)',not running('xfdesktop'))
check('the taskbar keeps running',state(panel.pid)!='T' and running('xfce4-panel'))
resume();time.sleep(3)
check('xfdesktop started again afterwards',running('xfdesktop') and not L.PAUSED_FILE.exists())
L.pause_desktop();time.sleep(2)  # a launcher stopped by Android before Studio ended...
L.resume_desktop();time.sleep(3)  # ...is made good by the next launcher start
check('a desktop left closed is started again by the next start',running('xfdesktop'))

stop.set();subprocess.run(['wineserver','-k'],env=env,**quiet)
for p in (desk,panel,wm):p.kill()
subprocess.run(['pkill','-x','xfdesktop'])
print('%d failed'%fails);sys.exit(1 if fails else 0)
