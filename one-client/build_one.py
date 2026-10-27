from pathlib import Path
import hashlib,json,re,zipfile

ROOT=Path(__file__).resolve().parent
INPUT=ROOT/'input.jar'
OUTPUT=ROOT.parent/'output'/'REX-One-1.21.11.jar'
VERSION='1.0.0-one.7'
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
  .one list [category|text]  every module, enabled ones bracketed; text filters by name
  .one mats                what the loaded blueprint still needs, before it stalls on it
  .one dupe [cmd <text>]   duper state, the patterns, and the command COMMAND_RACE sends
  .one toggle <module>     also: .one on <module> | .one off <module>  (prefixes work)
  .one config save <id> [name] | load <id> | list | flush
  .one donut               legacy Donut settings screen
  .one keys                this list
  .autobuild | .build      setup | start | pause | resume | stop | status | list | creative on/off

Modules
  KillAura and Auto Totem were already registered and are still where they were, in COMBAT, next to
  AutoCrystal, TriggerBot, AutoInvTotem, TotemOffhand and HoverTotem; ".one list combat" prints the
  row. What was wrong is that registerAll was one long sequence of constructor calls, so a module
  that threw on construction silently took every module after it off the menu. Each of the 113 rows
  now stands on its own and a failure is named in ".one status".

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
  The plain click race is dead and has been since 1.17: every click carries the menu's revision now,
  and on a mismatch the server resends the whole menu instead of applying anything. That is why the
  earlier version did nothing. What is left is not in vanilla's hands, so this aims there instead:
  plugin menus that handle one click more than once, plugin menus that never check which window a
  click came from, economy commands that read an inventory before they empty it, and the save that
  runs when a connection dies. It works on whatever is in your hand.
    AUTO           the default: one run per pattern, counted separately, and it names the one that
                   moved the number, or says plainly that this server leaves no seam open
    GUI_RACE       several left clicks on one plugin-menu slot inside a single tick
    SHIFT_SPAM     several shift-clicks on one slot inside a tick: sell slots, deposit slots, bags
    DOUBLE_PICKUP  pick up, then collect every match onto the cursor, repeatedly
    SWAP_RACE      swap the slot into a hotbar slot and throw it in the same tick
    STALE_WINDOW   keeps clicking the window id the plugin has already replaced
    DROP_SPAM      throws from your own inventory window while another menu is open; works anywhere
    COMMAND_RACE   one command several times in a tick; set it with .one dupe cmd /sell all
    LOGOUT         drops the stack, then kills the socket with no logout packet, so the drop races
                   the save; reconnect and compare, nothing can be counted for you while gone
    HELD_THROW     the vanilla race, kept as the baseline the others are measured against
  Target HELD finds the held stack wherever it sits in the open menu, hotbar first; Target SLOT uses
  the index you name. Keep Stack throws one item per click instead of the whole stack, so a probe
  that fails costs one item. Clicks per burst, Bursts per run, Burst Gap in ticks, Settle before counting,
  Arm Timeout in seconds while it waits for the item, Stop On Gain to end the sweep on the first
  gain. Toggling it on prints what the pattern does and what to set up, then it names whatever it is
  still waiting for.
  Counting covers your 36 inventory slots plus the open container's own slots, by item, so a copy
  that lands anywhere you can reach is counted. The count is the verdict; there is no claim here
  that any of it works on a given server.
  Click and command spam of this shape is exactly what server-side logging looks for, so it is an
  account risk, and LOGOUT ends the session by design.

Duper HUD (Render)
  On by default, and it draws again after an open screen so a container cannot hide it. It uses the
  game's own fill and text calls rather than the client's painter: the painter sets up and tears
  down renderer state around each batch, which is correct in the HUD pass it was written for and
  wrong at the tail of a screen's own render, where it left the projection and clip changed under
  the screen still drawing — that is what zoomed and cut the chat screen. The panel
  carries the pattern and, while idle, what that pattern is for; then the phase and whatever it is
  waiting for, the run number, the stack it picked, the slot and window id it is clicking, the count
  as it moves with the change called out in green, how much it has sent, a burst progress bar, every
  pattern already tried with its result, and the last few things that happened. Anchor, X, Y, Width
  and Log Lines place and size it; Background, Accent, Text and Gain colour it. The screen pass is
  not a required mixin: if it ever misses a version, the panel stays behind the container backdrop
  rather than failing the client.

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
    Trust Prediction finishes a placement on the client's own prediction instead of the server's
                     acknowledgement, after Prediction Delay ticks. Off by default: it gives up the
                     confirmation this build is built around, and leans on the revalidation pass and
                     the final whole-structure check to re-queue anything the server refused. Worth
                     it on a high ping, where the acknowledgement wait is the whole cost.
    Auto Resume      re-queues deferred blocks after unloaded chunks, lost routes, missing supports
                     and occupied positions, with Retry Delay between tries, backing off every
                     eight tries, and Max Retries without confirmed progress before it stops. A
                     pause you have to clear yourself — your own pause, Freecam, an open menu, death,
                     a vehicle, missing materials, world bounds, a disconnect — is never resumed.
  Exact-state checks and the four-tick stability window are untouched, and the acknowledgement wait
  stays in place unless you switch Trust Prediction on yourself.

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
            if name.startswith(('net/minecraft/','net/fabricmc/','org/spongepowered/')):continue
            patched[name]=path.read_bytes()
    sources={'one-client-source/'+path.relative_to(ROOT).as_posix():path.read_bytes()
             for path in list((ROOT/'src').rglob('*.java'))+list((ROOT/'patch').rglob('*.java'))
             +list((ROOT/'tools-src').rglob('*.py'))+[ROOT/'build_one.py',ROOT/'build.sh']}

    with zipfile.ZipFile(INPUT) as jar:
        metadata=json.loads(jar.read('fabric.mod.json'))
        mixins=json.loads(jar.read('rex-fantasy.mixins.json'))
        if 'DuperHudMixin' not in mixins['client']:mixins['client'].append('DuperHudMixin')
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
        replaced['rex-fantasy.mixins.json']=(json.dumps(mixins,indent=2)+'\n').encode()
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
        assert 'DuperHudMixin' in json.loads(out.read('rex-fantasy.mixins.json'))['client']
        assert not any(n.startswith(('TickHostUnderTest','TempoStub','OnePatch')) for n in names)
        assert ENTRY.replace('.','/')+'.class' in names
        for module in ('rex/LightESP','rex/BuildTempo','rex/BuildPatience','rex/Duper','rex/DuperPattern',
                       'rex/DonutFeature','rex/DonutFly','rex/DonutStorage','rex/DonutSpawner','rex/DonutChunks',
                       'rex/DonutHud','rex/Modules','rex/DuperTarget','rex/DuperHud','rex/HudAnchor',
                       'rex/mixin/DuperHudMixin'):assert module+'.class' in names
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
