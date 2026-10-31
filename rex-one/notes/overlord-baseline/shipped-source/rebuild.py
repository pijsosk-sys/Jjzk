#!/usr/bin/env python3
"""Rebuild the modified uploaded mod using JDK 21; compile-only stubs are excluded."""
import pathlib,tempfile,zipfile,subprocess,sys,shutil
if len(sys.argv)!=3:raise SystemExit('usage: python3 rebuild.py input.jar output.jar (JDK 21)')
source,out=map(lambda p:pathlib.Path(p).resolve(),sys.argv[1:])
if source==out:raise SystemExit('Choose a separate output file.')
compiler=shutil.which('javac')
if not compiler:raise SystemExit('Add JDK 21 javac to PATH.')
with tempfile.TemporaryDirectory() as temp:
 root=pathlib.Path(temp);files=[]
 with zipfile.ZipFile(source) as z:
  for n in z.namelist():
   if n.startswith(('overlord-source/src/','overlord-source/compile-only/')) and n.endswith('.java'):
    p=root/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n));files.append(str(p))
  subprocess.run([compiler,'--release','21','-proc:none','-implicit:none','-sourcepath',str(root/'empty'),'-cp',str(source),'-d',str(root/'classes'),*files],check=True)
  compiled={str(p.relative_to(root/'classes')):p.read_bytes() for p in (root/'classes'/'com').rglob('*.class')}
  with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as target:
   for n in z.namelist():target.writestr(n,compiled.pop(n,z.read(n)))
   for n,data in compiled.items():target.writestr(n,data)
print(out)
