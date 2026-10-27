#!/usr/bin/env python3
"""Intermediary <-> Mojang name index for one Minecraft version.

Inputs, both downloadable without an account:
  client.txt           Mojang's own mappings for the version (named -> official obfuscated)
  mappings.tiny        Fabric intermediary, mappings/mappings.tiny inside
                       maven.fabricmc.net/net/fabricmc/intermediary/<version>/intermediary-<version>-v2.jar

  python3 mapidx.py <dir> class net.minecraft.world.entity.Entity
  python3 mapidx.py <dir> class class_1297
  python3 mapidx.py <dir> member class_1297 method_23317
  python3 mapidx.py <dir> find Entity.getX
  python3 mapidx.py <dir> ctor class_2813
"""
import re,sys,zipfile
from pathlib import Path

PRIMITIVES={'void':'V','boolean':'Z','byte':'B','char':'C','short':'S','int':'I','long':'J','float':'F','double':'D'}

def jvm_type(text,named):
    arrays=0
    while text.endswith('[]'):
        arrays+=1;text=text[:-2]
    if text in PRIMITIVES:
        core=PRIMITIVES[text]
    else:
        core='L'+named.get(text,text).replace('.','/')+';'
    return '['*arrays+core

def signature_desc(signature,named):
    """'1:2:double getX(int,long)' -> '(IJ)D' in the official namespace."""
    body=signature.split(':')[-1]
    returns,rest=body.split(' ',1)
    args=rest[rest.index('(')+1:rest.rindex(')')]
    parts=[a for a in args.split(',') if a]
    return '('+''.join(jvm_type(a,named) for a in parts)+')'+jvm_type(returns,named)

def load(folder):
    folder=Path(folder)
    official={}   # obf class -> intermediary class
    members={}    # (obf class, obf member, desc or None) -> intermediary name
    text=(folder/'mappings'/'mappings.tiny')
    if not text.exists():
        with zipfile.ZipFile(next(folder.glob('intermediary*.jar'))) as jar:
            lines=jar.read('mappings/mappings.tiny').decode().splitlines()
    else:
        lines=text.read_text().splitlines()
    owner=None
    for line in lines:
        part=line.split('\t')
        if line.startswith('c\t'):
            owner=part[1];official[part[1]]=part[2].split('/')[-1]
        elif line.startswith('\tm\t') or line.startswith('\tf\t'):
            members[(owner,part[3],part[2])]=part[4] if len(part)>4 else part[3]
    named={}      # named class -> obf class
    fields={}     # (named class, named member) -> obf member
    methods={}
    owner=None
    for line in (folder/'client.txt').read_text().splitlines():
        if line.startswith('#'):continue
        if not line.startswith(' '):
            left,right=line.rstrip(':').split(' -> ');named[left]=right;owner=left
        elif owner is not None:
            body=line.strip()
            left,right=body.split(' -> ')
            if '(' in left:
                methods.setdefault((owner,left.split('(')[0].split(' ')[-1]),[]).append((right,left))
            else:
                fields[(owner,left.split(' ')[-1])]=right
    return official,members,named,fields,methods

def main():
    folder,action,*rest=sys.argv[1:]
    official,members,named,fields,methods=load(folder)
    obf_to_named={v:k for k,v in named.items()}
    inter_to_obf={v:k for k,v in official.items()}
    if action=='class':
        key=rest[0]
        if key.startswith('class_') or key.startswith('net/minecraft/class_'):
            obf=inter_to_obf[key.split('/')[-1]]
            print(key,'->',obf,'->',obf_to_named.get(obf,'?'))
        else:
            obf=named[key.replace('/','.')]
            print(key,'->',obf,'->',official.get(obf,'?'))
    elif action=='member':
        cls,member=rest
        obf=inter_to_obf[cls]
        name=obf_to_named.get(obf)
        for (owner,obfm,desc),inter in members.items():
            if owner!=obf or inter!=member:continue
            print(f'{cls}.{member} desc={desc} obf={obf}.{obfm}')
            for (c,pretty),entries in methods.items():
                if c!=name:continue
                for o,signature in entries:
                    if o==obfm and signature_desc(signature,named)==desc:
                        print('   named:',signature.split(':')[-1])
            for (c,f),o in fields.items():
                if c==name and o==obfm:print('   named field:',f)
    elif action=='find':
        cls,member=rest[0].split('.')
        for name,obf in named.items():
            if name.split('.')[-1]!=cls:continue
            for (owner,pretty),entries in methods.items():
                if owner!=name or pretty!=member:continue
                for obfm,signature in entries:
                    inter=[v for (o,m,d),v in members.items() if o==obf and m==obfm]
                    print(f'{name}.{signature} -> {official.get(obf)}.{inter}')
            if (name,member) in fields:
                obfm=fields[(name,member)]
                inter=[v for (o,m,d),v in members.items() if o==obf and m==obfm]
                print(f'{name}.{member} (field) -> {official.get(obf)}.{inter}')
    elif action=='ctor':
        key=rest[0]
        obf=inter_to_obf[key] if key.startswith('class_') else named[key]
        name=obf_to_named.get(obf,key)
        for (owner,pretty),entries in methods.items():
            if owner==name and pretty.split('.')[-1]==name.split('.')[-1]:
                for obfm,signature in entries:print(name,signature,'->',obfm)
        with zipfile.ZipFile(Path(folder)/'client.jar') as jar:
            import subprocess,tempfile
            with tempfile.TemporaryDirectory() as temp:
                target=Path(temp)/(obf.replace('.','/')+'.class')
                target.parent.mkdir(parents=True,exist_ok=True)
                target.write_bytes(jar.read(obf.replace('.','/')+'.class'))
                out=subprocess.run(['javap','-p','-cp',temp,obf],capture_output=True,text=True).stdout
        for line in out.splitlines():
            if obf.split('.')[-1]+'(' in line or ' '+obf+'(' in line:
                print('obf ctor:',line.strip())
                for raw in re.findall(r'[A-Za-z_$][\w.$]*',line):
                    if raw in official:print('   ',raw,'=',official[raw],'=',obf_to_named.get(raw))
    else:
        raise SystemExit(__doc__)

if __name__=='__main__':main()
