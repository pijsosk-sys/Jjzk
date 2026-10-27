#!/usr/bin/env python3
"""Checks every intermediary name our sources reach for by string against the real mappings.

  python3 checknames.py <maps dir> <source dir>...

A name that does not exist in this Minecraft version at all is a certain bug, and reflection hides
it until the feature runs. Prints one line per name with the class and the Mojang name behind it,
and exits non-zero on anything it cannot find.
"""
import importlib.util,re,sys
from pathlib import Path

def load(maps):
    spec=importlib.util.spec_from_file_location("mapidx",str(Path(__file__).with_name('mapidx.py')))
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module,module.load(maps)

def main():
    maps,sources=sys.argv[1],sys.argv[2:]
    mapidx,(official,members,named,fields,methods)=load(maps)
    obf2named={v:k for k,v in named.items()}
    owners={}
    for (owner,obfm,desc),inter in members.items():
        owners.setdefault(inter,[]).append((official.get(owner,owner),desc,obfm,obf2named.get(owner)))
    wanted={}
    for source in sources:
        for path in Path(source).rglob('*.java'):
            text=path.read_text()
            for name in re.findall(r'"((?:method|field)_\d+)"',text):
                wanted.setdefault(name,set()).add(path.name)
    missing=[]
    for name in sorted(wanted):
        hits=owners.get(name)
        if not hits:
            missing.append(name)
            print(f'MISSING {name:16} used by {", ".join(sorted(wanted[name]))}')
            continue
        owner,desc,obfm,named_owner=hits[0]
        label=''
        if named_owner:
            for (c,pretty),entries in methods.items():
                if c!=named_owner:continue
                for o,sig in entries:
                    if o==obfm and mapidx.signature_desc(sig,named)==desc:label=sig.split(':')[-1]
            if not label:
                for (c,f),o in fields.items():
                    if c==named_owner and o==obfm:label='field '+f
        print(f'ok      {name:16} {owner:14} {label or desc}')
    print(f'{len(wanted)} names, {len(missing)} missing')
    return 1 if missing else 0

if __name__=='__main__':sys.exit(main())
