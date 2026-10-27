#!/usr/bin/env python3
"""Repack the Boddy Studio Compat APK with the launcher from this directory.

Usage: rebuild_apk.py ORIGINAL.apk OUT-unsigned.apk VERSION_CODE
Then: zipalign -P 16 -f 4 OUT-unsigned.apk OUT-aligned.apk && apksigner sign --ks KEY OUT-aligned.apk

Only assets/boddy/studio, assets/boddy/studio-app.sh, assets/boddy-studio-fix-source.zip and the manifest versionCode change.
Every other entry is copied byte-for-byte with its original compression method (resources.arsc stays STORED).
"""
import io,struct,sys,zipfile
from pathlib import Path
here=Path(__file__).resolve().parent
src,out,version=Path(sys.argv[1]),Path(sys.argv[2]),int(sys.argv[3])
def u32(b,o):return struct.unpack_from('<I',b,o)[0]
def set_version_code(b):
 h=struct.unpack_from('<H',b,10)[0];count=u32(b,16);start=u32(b,28);assert not u32(b,24)&256
 names=[]
 for i in range(count):
  o=8+start+u32(b,8+h+4*i);n=struct.unpack_from('<H',b,o)[0];names.append(bytes(b[o+2:o+2+n*2]).decode('utf-16le'))
 changed=[];pos=8
 while pos<len(b):
  typ,_,size=struct.unpack_from('<HHI',b,pos)
  if typ==0x102 and names[u32(b,pos+20)]=='manifest':
   astart,asz,n=struct.unpack_from('<HHH',b,pos+24)
   for i in range(n):
    off=pos+16+astart+i*asz
    if names[u32(b,off+4)]=='versionCode':
     assert b[off+15] in (16,17);changed.append(u32(b,off+16));struct.pack_into('<I',b,off+16,version)
  pos+=size
 assert len(changed)==1 and changed[0]<version,changed
 return changed[0]
replace={'assets/boddy/'+n:(here/n).read_bytes() for n in ('studio','studio-app.sh')}
with zipfile.ZipFile(src) as z,zipfile.ZipFile(out,'w') as w:
 for info in z.infolist():
  if info.filename.startswith('META-INF/'):continue
  data=z.read(info.filename)
  if info.filename=='AndroidManifest.xml':
   data=bytearray(data);old=set_version_code(data);data=bytes(data)
  elif info.filename=='assets/boddy-studio-fix-source.zip':
   buf=io.BytesIO()
   with zipfile.ZipFile(io.BytesIO(data)) as a,zipfile.ZipFile(buf,'w',zipfile.ZIP_DEFLATED) as b:
    ours={'assets/boddy/studio':'studio','assets/boddy/studio-app.sh':'studio-app.sh','test_launcher.py':'test_launcher.py','README.md':'README.md','rebuild_apk.py':'rebuild_apk.py','manifest.txt':'manifest.txt'}
    for n in a.namelist():
     if n not in ours and n!='studio_runtime.py':b.writestr(n,a.read(n))
    for n,f in ours.items():b.writestr(n,(here/f).read_bytes())
   data=buf.getvalue()
  elif info.filename in replace:data=replace.pop(info.filename)
  w.writestr(info,data,compress_type=info.compress_type)
assert not replace,replace
print('versionCode %d -> %d; wrote %s'%(old,version,out))
