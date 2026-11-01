#!/usr/bin/env python3
"""Unit tests for the Studio launcher's setup ladder and settings, run on a PC without Wine or Studio: the
steps that install or run Windows programs are replaced by stand-ins, the decisions are the launcher's own.
Usage: python3 test_launcher.py /path/to/studio"""
import os,sys,json,tempfile,unittest
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from launcher_module import load
STUDIO=sys.argv.pop(1) if len(sys.argv)>1 else os.path.join(os.path.dirname(__file__),'..','studio')
os.environ['HOME']=tempfile.mkdtemp(prefix='boddy-test-home-')
L=load(STUDIO)

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

if __name__=='__main__':unittest.main(verbosity=2)
