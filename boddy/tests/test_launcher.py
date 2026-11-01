#!/usr/bin/env python3
"""Unit tests for the Studio launcher's setup ladder and settings, run on a PC without Wine or Studio: the
steps that install or run Windows programs are replaced by stand-ins, the decisions are the launcher's own.
Usage: python3 test_launcher.py /path/to/studio"""
import os,sys,json,tempfile,time,unittest
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from launcher_module import load
STUDIO=sys.argv.pop(1) if len(sys.argv)>1 else os.path.join(os.path.dirname(__file__),'..','studio')
os.environ['HOME']=tempfile.mkdtemp(prefix='boddy-test-home-')
L=load(STUDIO)
PATH=os.environ['PATH']

class Launch:
 """try_every_way() with stand-ins: every launch 'runs' Studio, which exits with `codes` in turn."""
 def __init__(self,codes):
  self.codes=list(codes);self.envs=[];self.setups=[];self.said=[]
  L.ROOT.mkdir(parents=True,exist_ok=True);L.CACHE.mkdir(parents=True,exist_ok=True)
  self.studio=L.ROOT/'Studio';(self.studio/'ClientSettings').mkdir(parents=True,exist_ok=True)
  prefix=L.ROOT/'prefix-test';prefix.mkdir(exist_ok=True)
  L.start_windows=lambda:(['wine'],L.wine_env(prefix),prefix)
  L.install_studio=lambda:self.studio
  for name in ('ensure_graphics','setup_browser_login','tune_prefix','install_mcp_plugin','set_window_mode'):
   setattr(L,name,lambda *a,**k:None)
  dxvk=L.ROOT/'dxvk-test';dxvk.mkdir(exist_ok=True)
  for n in L.DXVK_DLLS:(dxvk/(n+'.dll')).write_bytes(b'MZ')
  L.install_dxvk=lambda:dxvk
  L.fit_display=lambda:(lambda:None)
  L.studio_log=lambda prefix,since:([],False)
  L.studio_crash_dump=lambda *a,**k:None
  L.say=lambda s:self.said.append(s)
  def run_studio(args,cwd,env):
   self.envs.append(env);self.setups.append(L.current_setup());return self.codes.pop(0),[]
  L.run_studio=run_studio
 def go(self):
  """One launch; returns the error it ended with, or None for a normal exit."""
  try:L.try_every_way(None)
  except RuntimeError as e:return str(e)
  except SystemExit:return None

def reset():
 for f in (L.SETUP_FILE,L.RENDERER_FILE,L.ROOT/'gpu-failed',L.ROOT/'preset'):
  f.unlink(missing_ok=True)
 for k in [k for k in os.environ if k.startswith('BODDY_')]:del os.environ[k]
 L.FORCED.clear()

class Box64Speed(unittest.TestCase):
 def setUp(self):reset()
 def test_first_launch_is_fastest(self):
  self.assertEqual(L.SETUPS[0][2],'fast')
  self.assertEqual(L.BOX64_SPEED['fast'],{'BOX64_DYNAREC_STRONGMEM':'0','BOX64_DYNAREC_BIGBLOCK':'2','BOX64_DYNAREC_CALLRET':'1'})
  run=Launch([0]);self.assertIsNone(run.go())
  env=run.envs[0]
  self.assertEqual((env['BOX64_DYNAREC_STRONGMEM'],env['BOX64_DYNAREC_BIGBLOCK'],env['BOX64_DYNAREC_CALLRET']),('0','2','1'))
 def test_crash_falls_back_to_previous_fast_values(self):
  err=Launch([-11]).go()
  self.assertIn('crashed',err);self.assertIn('fast with memory barriers',err)
  self.assertEqual(L.SETUP_FILE.read_text(),'1')
  run=Launch([0]);run.go();env=run.envs[0]
  # The values "fast" had before this change: STRONGMEM=1 and Box64's defaults for the rest.
  self.assertEqual(env['BOX64_DYNAREC_STRONGMEM'],'1')
  self.assertNotIn('BOX64_DYNAREC_BIGBLOCK',env);self.assertNotIn('BOX64_DYNAREC_CALLRET',env)
  self.assertEqual(run.setups[0]['renderer'],'DXVK')
 def test_rest_of_ladder_unchanged(self):
  # After the new first step, the ladder is the one shipped before (with its "fast" now called strongmem).
  old=(('DXVK',False,'fast'),('WineVulkan',False,'fast'),('WineOpenGL',False,'fast'),('DXVK',False,'safe'),
   ('WineOpenGL',False,'safe'),('WineOpenGL',True,'safe'))
  rename={'fast':'strongmem','safe':'safe'}
  self.assertEqual(tuple(s for s in L.SETUPS[1:] if s[0]!='GPU'),tuple((r,a,rename[sp]) for r,a,sp in old))
 def test_crash_report_after_every_setup(self):
  n=len(L.SETUPS);L.SETUP_FILE.write_text(str(n-1))
  err=Launch([-11]).go()
  self.assertIn('with every setup Boddy has (%d)'%n,err);self.assertEqual(L.SETUP_FILE.read_text(),'0')
 def test_ladder_restarts_once_for_new_first_step(self):
  for v in range(2,8):(L.ROOT/('.choice-v%d'%v)).touch()
  (L.ROOT/'.choice-v8').unlink(missing_ok=True);L.SETUP_FILE.write_text('3')
  seen=[]
  orig=L.try_every_way;L.try_every_way=lambda place:seen.append(L.current_setup()['step'])
  os.environ['DISPLAY']=':99'
  try:L.main()
  finally:L.try_every_way=orig
  self.assertEqual(seen,[0]);self.assertTrue((L.ROOT/'.choice-v8').exists())

class BackgroundLoad(unittest.TestCase):
 def setUp(self):reset()
 def test_firefox_does_not_prefetch(self):
  p=L.FIREFOX_PREFS
  self.assertIs(p['network.prefetch-next'],False);self.assertIs(p['network.dns.disablePrefetch'],True)
  self.assertIs(p['network.predictor.enabled'],False);self.assertEqual(p['network.http.speculative-parallel-limit'],0)
  self.assertIs(p['browser.urlbar.speculativeConnect.enabled'],False)
 def test_process_summary(self):
  import re
  m=re.fullmatch(r'(\d+) of 32 \((.+)\)',L.process_summary(top=1000))
  self.assertTrue(m);self.assertGreater(int(m.group(1)),0);self.assertIn('python3',m.group(2))
 def test_launch_logs_process_counts(self):
  run=Launch([0]);run.go()
  self.assertTrue(any(s.startswith('Processes before Studio starts: ') for s in run.said))
  self.assertTrue(any(s.startswith('Processes at the end: ') and 'most while it ran' in s for s in run.said))
 def test_monitor_lists_processes_when_their_number_changes(self):
  import threading
  stop=threading.Event();path,thread=L.start_monitor(stop);time.sleep(0.5);stop.set();thread.join(5)
  lines=open(path).read().splitlines()
  self.assertRegex(lines[2],r'^[0-9:]+ processes=\d+ mem=');self.assertTrue(lines[3].lstrip().startswith('running: '))
  self.assertGreater(L.MOST_PROCESSES[0],0)

class Presets(unittest.TestCase):
 def setUp(self):reset()
 def flags(self):
  return json.load(open(L.ROOT/'Studio'/'ClientSettings'/'ClientAppSettings.json'))
 def test_default_is_potato(self):
  run=Launch([0]);run.go();f=self.flags()
  self.assertEqual((f['DFIntTaskSchedulerTargetFps'],f['DFIntDebugDynamicRenderKiloPixels']),(15,90))
  self.assertIn('FFlagDisablePostFx',f)  # the other light settings stay
  self.assertTrue(any('Potato preset (15 fps' in s for s in run.said))
 def test_video_memory_follows_preset(self):
  for name,mb in {'Potato':512,'Lower':768,'Low':1024}.items():
   L.PRESET_FILE.write_text(name);run=Launch([0]);run.go()
   conf=(L.ROOT/'dxvk.conf').read_text()
   self.assertIn('dxgi.maxDeviceMemory = %d\n'%mb,conf,name);self.assertIn('dxvk.numCompilerThreads = 1',conf)
   self.assertTrue(run.envs[0]['DXVK_CONFIG_FILE'].endswith('dxvk.conf'))
  L.PRESET_FILE.write_text('Potato');L.RENDERER_FILE.write_text('WineOpenGL');run=Launch([0]);run.go()
  self.assertIn('VideoMemorySize=512',run.envs[0]['WINE_D3D_CONFIG'])
 def test_memory_logged(self):
  run=Launch([0]);run.go()
  self.assertTrue(any(s.startswith('Memory: ') and 'MB' in s for s in run.said))
  self.assertTrue(any(s.startswith('Memory at the end: ') for s in run.said))
  self.assertRegex(L.memory_summary(),r'^\S.* \d+MB')
 def test_each_preset_reaches_studio(self):
  for name,(fps,kpx) in {'Lower':(20,160),'Potato':(15,90),'Low':(24,240)}.items():
   sys.argv=['studio','--use-preset='+name];L.main()
   Launch([0]).go();f=self.flags()
   self.assertEqual((f['DFIntTaskSchedulerTargetFps'],f['DFIntDebugDynamicRenderKiloPixels']),(fps,kpx),name)
 def test_bad_preset_refused(self):
  sys.argv=['studio','--use-preset=Ultra']
  with self.assertRaises(RuntimeError):L.main()
 def test_full_graphics_still_wins(self):
  L.PRESET_FILE.write_text('Potato');os.environ['BODDY_STUDIO_FULL_GRAPHICS']='1'
  (L.ROOT/'Studio'/'ClientSettings').mkdir(parents=True,exist_ok=True)
  (L.ROOT/'Studio'/'ClientSettings'/'ClientAppSettings.json').write_text('{}')
  Launch([0]).go();self.assertNotIn('DFIntTaskSchedulerTargetFps',self.flags())

class SignIn(unittest.TestCase):
 def test_signin_browser_closed_once_studio_has_the_login(self):
  import subprocess
  # Stand-ins that run until stopped, named firefox-esr (argv[0]) with Firefox's arguments.
  wait=['-c','import time;time.sleep(60)']
  other=subprocess.Popen(['/usr/lib/firefox-esr/firefox-esr',*wait],executable=sys.executable)  # the user's own Firefox
  signin=subprocess.Popen(['/usr/lib/firefox-esr/firefox-esr',*wait,'--profile',str(L.ROOT/'firefox-profile')],executable=sys.executable)
  time.sleep(0.5);self.assertIsNone(signin.poll())
  L.close_signin_browser()
  self.assertIsNotNone(signin.wait(5));self.assertIsNone(other.poll());other.kill()

class SignInBrowser(unittest.TestCase):
 URL='https://www.roblox.com/login?returnUrl=x'
 def setUp(self):
  reset();L.SIGNIN_FILE.unlink(missing_ok=True)
  self.firefox=[];L.open_in_firefox=lambda url:self.firefox.append(url)
  self.ctl=os.path.join(tempfile.mkdtemp(),'boddy-ctl');L.BODDY_CTL=self.ctl
  L.say=lambda s:None
 def make_ctl(self,code):
  open(self.ctl,'w').write('#!/bin/sh\necho "$@" >"%s.args"\necho ok\nexit %d\n'%(self.ctl,code));os.chmod(self.ctl,0o755)
 def test_phone_browser_by_default(self):
  self.make_ctl(0);L.open_in_browser(self.URL)
  self.assertEqual(open(self.ctl+'.args').read().split(),['phone-open',self.URL]);self.assertEqual(self.firefox,[])
 def test_firefox_when_the_phone_cannot_be_reached(self):
  self.make_ctl(1);L.open_in_browser(self.URL);self.assertEqual(self.firefox,[self.URL])
  os.unlink(self.ctl);L.open_in_browser(self.URL);self.assertEqual(len(self.firefox),2)
 def test_firefox_when_chosen(self):
  self.make_ctl(0);sys.argv=['studio','--use-signin=firefox'];L.main()
  L.open_in_browser(self.URL);self.assertEqual(self.firefox,[self.URL]);self.assertFalse(os.path.exists(self.ctl+'.args'))
  sys.argv=['studio','--use-signin=phone'];L.main();L.open_in_browser(self.URL);self.assertTrue(os.path.exists(self.ctl+'.args'))
 def test_bad_links_refused(self):
  self.make_ctl(0)
  for bad in ('file:///etc/passwd','javascript:alert(1)','https://x y'):
   with self.assertRaises(ValueError):L.open_in_browser(bad)
  self.assertFalse(os.path.exists(self.ctl+'.args'))

class ScreenDepth(unittest.TestCase):
 def setUp(self):reset()
 def tearDown(self):
  try:os.unlink('/tmp/boddy-vnc-depth')
  except OSError:pass
 def launch(self,depth):
  open('/tmp/boddy-vnc-depth','w').write(depth+'\n');run=Launch([0]);run.go();return run.said
 def test_warns_on_16_bit_screen(self):
  self.assertTrue(any('16-bit colours' in s for s in self.launch('16')))
 def test_quiet_on_24_bit_screen(self):
  self.assertFalse(any('16-bit colours' in s for s in self.launch('24')))

TURNIP_ARCHIVE=os.getenv('BODDY_TEST_TURNIP')  # the downloaded Turnip archive, so no test needs the internet
class GpuMode(unittest.TestCase):
 def setUp(self):
  reset();self.kgsl=os.path.join(tempfile.mkdtemp(),'kgsl-3d0');L.KGSL_DEVICE=self.kgsl
  self.bin=tempfile.mkdtemp();os.environ['PATH']=self.bin+':'+PATH  # a stand-in vulkaninfo goes first
  L.ensure_graphics=lambda *a:None
  if TURNIP_ARCHIVE:
   import shutil;L.CACHE.mkdir(parents=True,exist_ok=True);shutil.copyfile(TURNIP_ARCHIVE,L.CACHE/os.path.basename(L.TURNIP_URL))
  L.RENDERER_FILE.write_text('GPU')
 def vulkaninfo(self,script):
  p=os.path.join(self.bin,'vulkaninfo');open(p,'w').write('#!/bin/sh\n'+script);os.chmod(p,0o755)
 def test_no_kgsl_device_falls_back_to_lavapipe(self):
  run=Launch([0]);run.go();env=run.envs[0]
  self.assertEqual(run.setups[0]['renderer'],'GPU')  # the choice stays; this launch draws on the processor
  self.assertNotIn('turnip',env.get('VK_ICD_FILENAMES',''));self.assertNotIn('MESA_VK_WSI_DEBUG',env)
  self.assertIn('d3d11,dxgi,d3d10core=n,b',env['WINEDLLOVERRIDES'])
  self.assertTrue(any('not using the GPU' in s and 'kgsl-3d0 is missing' in s for s in run.said),run.said)
 @unittest.skipUnless(TURNIP_ARCHIVE,'set BODDY_TEST_TURNIP to the Turnip archive')
 def test_gpu_used_when_device_and_driver_work(self):
  open(self.kgsl,'w').close()
  self.vulkaninfo('echo "	deviceName         = Turnip Adreno (TM) 740"')
  run=Launch([0]);run.go();env=run.envs[0]
  icd=env['VK_ICD_FILENAMES'];self.assertIn('turnip-'+L.TURNIP_VERSION,icd);self.assertEqual(env['VK_DRIVER_FILES'],icd)
  lib=json.load(open(icd))['ICD']['library_path'];self.assertTrue(os.path.isfile(lib))
  self.assertEqual(open(lib,'rb').read(4),b'\x7fELF')
  self.assertEqual(env['MESA_VK_WSI_DEBUG'],'sw');self.assertIn('d3d11,dxgi,d3d10core=n,b',env['WINEDLLOVERRIDES'])
  self.assertTrue(any(s.startswith('GPU: Turnip Adreno') for s in run.said))
 @unittest.skipUnless(TURNIP_ARCHIVE,'set BODDY_TEST_TURNIP to the Turnip archive')
 def test_driver_that_does_not_start_falls_back(self):
  open(self.kgsl,'w').close()
  self.vulkaninfo('echo "ERROR: vkCreateInstance failed: VK_ERROR_INCOMPATIBLE_DRIVER" >&2; exit 1')
  run=Launch([0]);run.go();env=run.envs[0]
  self.assertNotIn('turnip',env.get('VK_ICD_FILENAMES',''))
  self.assertTrue(any('GPU driver did not start' in s and 'INCOMPATIBLE_DRIVER' in s for s in run.said),run.said)
 @unittest.skipUnless(TURNIP_ARCHIVE,'set BODDY_TEST_TURNIP to the Turnip archive')
 def test_crash_on_gpu_falls_back_without_using_up_a_setup(self):
  open(self.kgsl,'w').close();self.vulkaninfo('echo "deviceName = Turnip Adreno (TM) 650"')
  err=Launch([-11]).go()
  self.assertIn("on the phone's GPU",err);self.assertIn('lavapipe',err)
  self.assertTrue(L.GPU_FAILED_FILE.exists());self.assertFalse(L.SETUP_FILE.exists())  # still the first setup
  run=Launch([0]);run.go();env=run.envs[0]
  self.assertEqual(run.setups[0]['renderer'],'DXVK');self.assertEqual(run.setups[0]['speed'],'fast')
  self.assertNotIn('turnip',env.get('VK_ICD_FILENAMES',''))
  self.assertTrue(any('crashed on the GPU last time' in s for s in run.said))
 def test_choosing_gpu_again_retries_it(self):
  L.GPU_FAILED_FILE.write_text('x');sys.argv=['studio','--use-renderer=GPU'];L.main()
  self.assertFalse(L.GPU_FAILED_FILE.exists());self.assertEqual(L.RENDERER_FILE.read_text(),'GPU')
  self.assertEqual(L.current_setup()['renderer'],'GPU')

if __name__=='__main__':unittest.main(verbosity=2)
