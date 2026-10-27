from pathlib import Path
import hashlib,json,re,zipfile

ROOT=Path(__file__).resolve().parent
INPUT=ROOT/'input.jar'
OUTPUT=ROOT.parent/'output'/'REX-One-1.21.11.jar'
VERSION='1.0.0-one.2'
NAME='REX One'
ENTRY='rex.OneClient'
NOTES='''REX One — Minecraft 1.21.11, Java 21, version """+VERSION+"""

Krypton is the client. Everything else that used to have its own menu, keybind set, HUD and title
screen button is now a Krypton module row or a chat command.

One entry point
  rex.OneClient starts the six former entry points in their old order, each inside its own guard:
  a subsystem that throws is reported and skipped instead of failing Fabric's entry point and
  taking the client down with it. ".one status" lists whatever did not come up.

One surface
  Right Shift opens the menu. No other key is bound: every Donut binding is gone, including its
  Right Alt menu and the O builder key, the Donut button on the title screen is removed, and the
  legacy Donut HUD starts off (Donut HUD, Render) so Krypton's HUD stands alone. The old settings
  screen is still there behind ".one donut" for the knobs that have no module row yet.

  .one                     subsystems, builder tempo, duper state
  .one list [category]     every module, enabled ones bracketed
  .one toggle <module>     also: .one on <module> | .one off <module>  (prefixes work)
  .one config save <id> [name] | load <id> | list | flush
  .one donut               legacy Donut settings screen
  .one keys                this list
  .autobuild | .build      setup | start | pause | resume | stop | status | list | creative on/off

Donut features as modules
  Fly (Misc)               Donut flight with its ability packets and save handling
  Storage Finder (Render)  Donut storage highlighter, per-type colours
  Spawner Finder (Render)  Donut spawner highlighter
  Growth Chunks (Base)     kelp, vine, amethyst and bamboo growth evidence
  Donut HUD (Render)       the legacy module list and builder progress lines
  Each row writes the Donut config and saves it, and follows along when you change the same
  setting in .one donut, so the two never disagree.

Light ESP (Render)
  Player boxes from a tick snapshot instead of a per-frame entity walk. Hitbox size, team colour
  and tab-list state are resolved on the scan tick, so a frame reads three coordinates and three
  interpolation fields per player, builds the box and draws; nothing is allocated per frame. Scan
  Interval trades freshness for cost, Max Targets bounds the draw, Range bounds both. Tab List Only
  keeps holograms, shop stands and NPC bodies out of the overlay by requiring a tab-list entry; if
  that lookup is unavailable it reports once and stands down rather than hiding players. Off by
  default, no keybind.

Duper (Misc)
  Container clicks sent faster than the server can answer them, in the orders that have
  historically desynchronised an open menu. Toggling it on runs once, bounded, and switches itself
  back off.
    Pattern        THROW_RACE   repeated throws of one slot against one local state
                   MOVE_RACE    shift-move and throw the same slot, same tick
                   CURSOR_SPLIT pick up and throw the cursor, repeatedly
                   CLOSE_RACE   shift-move, then close the container in the same tick
    Clicks         clicks per burst, all inside one client tick
    Bursts         bursts per run; Burst Gap puts ticks between them, zero means one tick
    Slot           menu slot index, or -1 for the held hotbar slot in whatever menu is open
    Button         throw 0 drops one item, 1 drops the stack
    Container Only refuses to run against the bare player inventory
    Report         chat line with the clicks sent and what the slot held before and after
  It stops if the menu changes. Whether any pattern does anything depends on that server's version
  and patches; the report is there so you can see for yourself rather than take a claim on faith.
  Click spam of this shape is exactly what server-side logging looks for, so it is an account risk.

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
  Every intermediary name and signature the new code calls was checked against Mojang's own 1.21.11
  mappings and Fabric intermediary for 1.21.11; tools-src/mapidx.py is that index and says where to
  get both. The patched dispatcher bytecode is hosted in a plain class and run by the JVM verifier,
  then driven through its idle, armed, tempo-off and stopped states; every patched class is analysed
  for stack and branch consistency. Minecraft itself was not launched here.
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
             for path in list((ROOT/'src').rglob('*.java'))+list((ROOT/'patch').rglob('*.java'))
             +list((ROOT/'tools-src').rglob('*.py'))+[ROOT/'build_one.py',ROOT/'build.sh']}

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
        for module in ('rex/LightESP','rex/BuildTempo','rex/BuildPatience','rex/Duper','rex/DuperPattern',
                       'rex/DonutFeature','rex/DonutFly','rex/DonutStorage','rex/DonutSpawner','rex/DonutChunks',
                       'rex/DonutHud'):assert module+'.class' in names
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
