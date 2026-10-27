from pathlib import Path
import hashlib,json,re,zipfile

ROOT=Path(__file__).resolve().parent
INPUT=ROOT/'input.jar'
OUTPUT=ROOT.parent/'output'/'REX-One-1.21.11.jar'
VERSION='1.0.0-one.1'
NAME='REX One'
ENTRY='rex.OneClient'
NOTES='''REX One — Minecraft 1.21.11, Java 21, version '''+VERSION+'''

One client. The six client entry points of the merged Donut/Krypton/REX build now start from
rex.OneClient, in their old order, each inside its own guard: a subsystem that throws is reported
and skipped instead of failing Fabric's entry point and taking the whole client down. "/.one status"
lists what came up.

Keys and commands
  Right Shift        the module menu (the only menu key)
  O                  Auto Build window
  .one               status of every subsystem plus the builder tempo
  .one donut         legacy Donut settings screen (its old Right Alt binding is gone)
  .one keys          this list
  .autobuild | .build  setup | start | pause | resume | stop | status | list | creative on/off

Light ESP (Render)
  Player boxes drawn from a tick snapshot instead of a per-frame entity walk. Hitbox size, team
  colour and tab-list state are resolved on the scan tick, so a frame reads three coordinates and
  three interpolation fields per player, builds the box and draws it; nothing is allocated per
  frame. Scan Interval trades freshness for cost, Max Targets bounds the draw, Range bounds both.
  Tab List Only keeps holograms, shop stands and NPC bodies out of the overlay on DonutSMP by
  requiring a tab-list entry; if that lookup is unavailable the filter reports once and stands down
  rather than hiding players. Health Tint reads health on the scan tick. Friends uses the existing
  friend list. Off by default, no keybind.

Build Tempo (Misc)
  Pacing for the unified builder, on by default; switch it off for the original cadence.
    Steps Per Tick   pipeline steps per client tick while nothing is in flight (was one). The
                     finish/choose/arm chain now fits in one tick instead of three. Walking and
                     waiting for the server still cost a whole tick each, so the placement packet
                     rate is unchanged: at most one per client tick, after the rotation it was
                     aimed with has been sent.
    Arm Delay        ticks between aiming and the packet (was two).
    Ack Window       ticks to wait for the server before re-checking a placement (was 60).
    Swap Cooldown    ticks after pulling material into the hotbar (was two).
    Layer Window     blueprint layers eligible at once (was one), fewer walk-backs per layer.
    Candidates       targets examined when choosing the next block (was 256).
    Auto Resume      re-queues deferred blocks after unloaded chunks, lost routes, missing supports
                     and occupied positions, with Retry Delay between tries, backing off every
                     eight tries, and Max Retries without confirmed progress before it stops. A
                     pause you have to clear yourself — your own pause, Freecam, an open menu, death,
                     a vehicle, missing materials, world bounds, a disconnect — is never resumed.
  Exact-state checks, server acknowledgement and the four-tick stability window are untouched.

What did not change
  Every other module, mixin, bundled Baritone and Netty jar, asset and resource pack is the merged
  build's own. Both camera mixins still coexist as fix 63 left them. The acknowledgement wait is
  still one placement in flight at a time, so ping, not the client, bounds large builds.

Validation
  The patched dispatcher bytecode is hosted in a plain class and run by the JVM verifier, then
  driven through its idle, armed, tempo-off and stopped states; every patched class is analysed for
  stack and branch consistency. Minecraft itself was not launched here.
  Sources: one-client-source/.
'''

def main():
    patched={}
    for folder in ('patched','build'):
        base=ROOT/folder
        for path in base.rglob('*.class'):
            name=path.relative_to(base).as_posix()
            if name.startswith(('net/minecraft/','net/fabricmc/')):continue
            patched[name]=path.read_bytes()
    sources={'one-client-source/'+path.relative_to(ROOT).as_posix():path.read_bytes()
             for path in list((ROOT/'src').rglob('*.java'))+list((ROOT/'patch').rglob('*.java'))+[ROOT/'build_one.py']}

    with zipfile.ZipFile(INPUT) as jar:
        metadata=json.loads(jar.read('fabric.mod.json'))
        legacy=metadata['entrypoints']['client']
        if ENTRY in legacy:raise ValueError('Already merged')
        metadata['name']=NAME
        metadata['version']=VERSION
        metadata['description']=('One client: a single guarded entry point for the merged Donut, Krypton and REX '
                                 'subsystems, one menu key, snapshot Light ESP and a paced Auto Build with automatic retry.')
        metadata['entrypoints']['client']=[ENTRY]
        manifest=('Manifest-Version: 1.0\r\n'
                  'Fabric-Mapping-Namespace: intermediary\r\n'
                  'Implementation-Title: '+NAME+'\r\n'
                  'Implementation-Version: '+VERSION+'\r\n\r\n')
        dropped=[name for name in jar.namelist()
                 if re.fullmatch(r'(README|REX).*\.(txt|md)',name) or name=='REX-Client29-Module-Audit.txt']
        if len(dropped)<30:raise ValueError('Expected the per-build readmes: '+str(len(dropped)))
        replaced=dict(patched)
        replaced['fabric.mod.json']=(json.dumps(metadata,indent=2)+'\n').encode()
        replaced['META-INF/MANIFEST.MF']=manifest.encode()
        OUTPUT.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(OUTPUT,'w',zipfile.ZIP_DEFLATED) as output:
            for item in jar.infolist():
                if item.filename in dropped:continue
                if item.is_dir():
                    output.writestr(item,b'');continue
                output.writestr(item,replaced.pop(item.filename,jar.read(item.filename)))
            extra={'README.txt':NOTES.encode()}
            extra.update(sources)
            extra.update(replaced)
            for name,data in sorted(extra.items()):output.writestr(name,data)

    with zipfile.ZipFile(OUTPUT) as out, zipfile.ZipFile(INPUT) as jar:
        assert out.testzip() is None
        names=out.namelist()
        assert len(names)==len(set(names)),'duplicate entries'
        assert not any(n.startswith(('net/minecraft/','net/fabricmc/api/','org/spongepowered/')) for n in names)
        assert not any(n.startswith(('TickHostUnderTest','TempoStub','OnePatch')) for n in names)
        assert ENTRY.replace('.','/')+'.class' in names
        for module in ('rex/LightESP','rex/BuildTempo','rex/BuildPatience'):assert module+'.class' in names
        for config in metadata['mixins']:
            mixins=json.loads(out.read(config));package=mixins['package'].replace('.','/')
            for name in mixins.get('client',[])+mixins.get('mixins',[]):
                assert package+'/'+name.replace('.','/')+'.class' in names,name
        for name in jar.namelist():
            if name.startswith(('META-INF/jars/','assets/','resourcepacks/')):
                assert jar.read(name)==out.read(name),name
        for name,data in patched.items():assert out.read(name)==data,name
        assert all(name in names for name in sources)
        assert not any(re.fullmatch(r'(README|REX).*\.(txt|md)',n) for n in names if '/' not in n and n!='README.txt')

    report={'output':str(OUTPUT),'size_bytes':OUTPUT.stat().st_size,
            'sha256':hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
            'entrypoint':ENTRY,'folded_entrypoints':legacy,
            'added_classes':sorted(patched),'dropped_readmes':len(dropped)}
    (ROOT/'package-report.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
