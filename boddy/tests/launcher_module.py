"""Imports the Python launcher embedded in boddy/studio as a module, for tests on a PC.
Usage: launcher=load('/path/to/studio') after setting HOME to a scratch directory."""
import importlib.util,re,sys,tempfile,os
def load(studio_path,name='boddy_launcher'):
    text=open(studio_path).read()
    m=re.search(r"<<'BODDY_PY'.*?\n(.*?)\nBODDY_PY\n",text,re.S)
    if not m:raise RuntimeError('No embedded launcher in '+studio_path)
    path=os.path.join(tempfile.mkdtemp(),name+'.py')
    open(path,'w').write(m.group(1))
    spec=importlib.util.spec_from_file_location(name,path)
    mod=importlib.util.module_from_spec(spec);sys.modules[name]=mod;spec.loader.exec_module(mod)
    return mod
