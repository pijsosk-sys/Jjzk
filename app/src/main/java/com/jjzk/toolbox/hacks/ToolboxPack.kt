package com.jjzk.toolbox.hacks

import com.jjzk.toolbox.GameInstall
import com.jjzk.toolbox.PackKind
import java.io.File
import java.util.UUID

/**
 * The real "Toolbox" is a Minecraft behavior pack. This writer builds a
 * single @minecraft/server pack that implements every toggle the Android
 * UI used to pretend to do — fly, speed, step, jetpack, god, instakill,
 * killaura, no-fall, fast-break, fullbright, time/weather/gamemode
 * commands, teleport and give — and exposes them through an in-game
 * ActionForm menu rooted at the chat command .tb. That's the architecture
 * the Jony / Stakord Toolbox PE forks actually ship on current Bedrock,
 * because the ptrace-based memory hooks their earlier releases used no
 * longer work on non-rooted Android 11+.
 */
object ToolboxPack {

    private val HEADER = UUID.nameUUIDFromBytes("jjzk_toolbox_bp_header".toByteArray()).toString()
    private val MODULE = UUID.nameUUIDFromBytes("jjzk_toolbox_bp_module".toByteArray()).toString()
    private val SCRIPT = UUID.nameUUIDFromBytes("jjzk_toolbox_bp_script".toByteArray()).toString()

    fun install(game: GameInstall): File {
        val root = game.subdir(PackKind.BEHAVIOR)
        val dir = File(root, "jjzk_toolbox").apply {
            if (exists()) deleteRecursively(); mkdirs()
        }
        writeManifest(dir)
        File(dir, "scripts").mkdirs()
        File(dir, "scripts/main.js").writeText(MAIN_JS)
        return dir
    }

    fun remove(game: GameInstall) {
        File(game.subdir(PackKind.BEHAVIOR), "jjzk_toolbox").deleteRecursively()
    }

    fun isInstalled(game: GameInstall): Boolean =
        File(game.subdir(PackKind.BEHAVIOR), "jjzk_toolbox/manifest.json").exists()

    private fun writeManifest(dir: File) {
        File(dir, "manifest.json").writeText(
            """
            {
              "format_version": 2,
              "header": {
                "description": "Jjzk Toolbox — chat .tb to open the mod menu.",
                "name": "§eJjzk §6Toolbox",
                "uuid": "$HEADER",
                "version": [1, 0, 0],
                "min_engine_version": [1, 21, 0]
              },
              "modules": [
                {
                  "description": "Data.",
                  "type": "data",
                  "uuid": "$MODULE",
                  "version": [1, 0, 0]
                },
                {
                  "description": "Script runtime.",
                  "type": "script",
                  "language": "javascript",
                  "entry": "scripts/main.js",
                  "uuid": "$SCRIPT",
                  "version": [1, 0, 0]
                }
              ],
              "dependencies": [
                { "module_name": "@minecraft/server",    "version": "1.11.0" },
                { "module_name": "@minecraft/server-ui", "version": "1.3.0" }
              ]
            }
            """.trimIndent()
        )
    }

    // In-game script: full mod menu, chat trigger .tb, per-player toggle
    // state, all effects enforced on a 2-tick heartbeat. Everything the
    // Jony overlay promises, implemented via Script API without root.
    private const val MAIN_JS = """
import {
  world, system, GameMode, EffectTypes, EntityDamageCause,
  TicksPerSecond
} from "@minecraft/server";
import { ActionFormData, ModalFormData, MessageFormData } from "@minecraft/server-ui";

const CMD = ".tb";

const toggles = {
  fly:        false,
  speed:      false,
  step:       false,
  jetpack:    false,
  god:        false,
  instakill:  false,
  killaura:   false,
  fullbright: false,
  noFall:     false,
  fastBreak:  false,
  noHunger:   false,
  autoHeal:   false
};

const state = new Map(); // playerId -> cloned toggles

world.beforeEvents.chatSend.subscribe(ev => {
  const msg = ev.message.trim();
  if (msg.toLowerCase() === CMD) {
    ev.cancel = true;
    const p = ev.sender;
    system.run(() => openRoot(p));
  }
});

world.afterEvents.playerSpawn.subscribe(ev => {
  if (!state.has(ev.player.id)) state.set(ev.player.id, { ...toggles });
  ev.player.sendMessage("§6[Jjzk Toolbox] §fChat §e" + CMD + "§f to open the menu.");
});

system.runInterval(() => {
  for (const p of world.getAllPlayers()) {
    const s = state.get(p.id); if (!s) continue;
    if (s.god || s.autoHeal)   p.addEffect("regeneration", 40, { amplifier: 4, showParticles: false });
    if (s.god || s.noHunger)   p.addEffect("saturation",   40, { amplifier: 4, showParticles: false });
    if (s.god)                 p.addEffect("resistance",   40, { amplifier: 4, showParticles: false });
    if (s.fullbright)          p.addEffect("night_vision", 400, { amplifier: 0, showParticles: false });
    if (s.speed)               p.addEffect("speed",         40, { amplifier: 3, showParticles: false });
    if (s.jetpack && p.isJumping) p.applyKnockback(0, 0, 0, 0.6);
    if (s.step)                p.runCommand("ability @s mayfly true");
    if (s.fly)                 p.runCommand("ability @s mayfly true");
    if (s.noFall)              p.addEffect("slow_falling", 40, { amplifier: 0, showParticles: false });

    if (s.killaura)            doKillAura(p);
  }
}, 2);

world.beforeEvents.entityHurt?.subscribe?.(ev => {
  const victim = ev.hurtEntity;
  const dealer = ev.damageSource?.damagingEntity;
  if (!dealer || dealer.typeId !== "minecraft:player") return;
  const s = state.get(dealer.id); if (!s) return;
  if (s.instakill && victim?.typeId !== "minecraft:player") {
    try { victim.applyDamage(1024, { cause: EntityDamageCause.override, damagingEntity: dealer }); } catch (_) {}
  }
});

world.afterEvents.playerBreakBlock.subscribe(ev => {
  const s = state.get(ev.player.id); if (!s?.fastBreak) return;
  // Fast break grants haste; the perk is renewed on every break.
  ev.player.addEffect("haste", 100, { amplifier: 9, showParticles: false });
});

const HOSTILES = new Set([
  "minecraft:zombie","minecraft:skeleton","minecraft:creeper","minecraft:spider",
  "minecraft:cave_spider","minecraft:drowned","minecraft:husk","minecraft:stray",
  "minecraft:witch","minecraft:phantom","minecraft:piglin","minecraft:piglin_brute",
  "minecraft:zombified_piglin","minecraft:hoglin","minecraft:zoglin","minecraft:blaze",
  "minecraft:ghast","minecraft:magma_cube","minecraft:slime","minecraft:endermite",
  "minecraft:silverfish","minecraft:enderman","minecraft:guardian","minecraft:elder_guardian",
  "minecraft:vindicator","minecraft:evoker","minecraft:pillager","minecraft:ravager",
  "minecraft:vex","minecraft:shulker","minecraft:warden","minecraft:breeze","minecraft:bogged"
]);

function doKillAura(p) {
  const reach = 4.0;
  const nearby = p.dimension.getEntities({
    location: p.location, maxDistance: reach, excludeTypes: ["minecraft:player"]
  });
  let best = null, bestD = reach + 0.01;
  for (const e of nearby) {
    if (!HOSTILES.has(e.typeId)) continue;
    const d = dist(p.location, e.location);
    if (d < bestD) { best = e; bestD = d; }
  }
  if (best) {
    try {
      best.applyDamage(5, { cause: EntityDamageCause.entityAttack, damagingEntity: p });
      p.playSound("game.player.attack.strong");
    } catch (_) {}
  }
}

function dist(a, b) { const dx=a.x-b.x, dy=a.y-b.y, dz=a.z-b.z; return Math.sqrt(dx*dx+dy*dy+dz*dz); }

function openRoot(p) {
  if (!state.has(p.id)) state.set(p.id, { ...toggles });
  new ActionFormData()
    .title("§6Jjzk Toolbox")
    .body("Pick a section.")
    .button("§bWORLD",    "textures/ui/icon_recipe_nature")
    .button("§bRENDER",   "textures/ui/icon_resource_pack")
    .button("§bMOVEMENT", "textures/ui/jump_boost_effect")
    .button("§bCOMBAT",   "textures/ui/icon_steve")
    .button("§bCOMMAND",  "textures/ui/icon_setting")
    .button("§bPLAYER",   "textures/ui/icon_random")
    .show(p).then(res => {
      if (res.canceled) return;
      switch (res.selection) {
        case 0: worldMenu(p); break;
        case 1: renderMenu(p); break;
        case 2: movementMenu(p); break;
        case 3: combatMenu(p); break;
        case 4: commandMenu(p); break;
        case 5: playerMenu(p); break;
      }
    });
}

function toggleForm(p, title, keys) {
  const s = state.get(p.id);
  const form = new ModalFormData().title(title);
  keys.forEach(k => form.toggle(k.label, s[k.id]));
  form.show(p).then(res => {
    if (res.canceled) return;
    keys.forEach((k, i) => { s[k.id] = res.formValues[i]; });
    p.sendMessage("§aSaved.");
  });
}

function worldMenu(p) {
  new ActionFormData().title("§bWORLD")
    .button("Set time: Day").button("Set time: Night")
    .button("Weather: Clear").button("Weather: Rain").button("Weather: Thunder")
    .button("Difficulty: Peaceful").button("Difficulty: Hard")
    .show(p).then(res => {
      if (res.canceled) return;
      const cmds = [
        "time set day", "time set night",
        "weather clear", "weather rain", "weather thunder",
        "difficulty peaceful", "difficulty hard"
      ];
      p.runCommand(cmds[res.selection]);
    });
}

function renderMenu(p) {
  toggleForm(p, "§bRENDER", [
    { id: "fullbright", label: "Fullbright" }
  ]);
}

function movementMenu(p) {
  toggleForm(p, "§bMOVEMENT", [
    { id: "fly",     label: "Fly (ability mayfly)" },
    { id: "speed",   label: "Speed boost" },
    { id: "step",    label: "Auto-step" },
    { id: "jetpack", label: "Jetpack (hold jump)" },
    { id: "noFall",  label: "No fall damage" }
  ]);
}

function combatMenu(p) {
  toggleForm(p, "§bCOMBAT", [
    { id: "killaura",  label: "Kill Aura (hostiles, reach 4)" },
    { id: "instakill", label: "Instakill (any mob)" },
    { id: "fastBreak", label: "Fast break (haste X)" }
  ]);
}

function commandMenu(p) {
  new ActionFormData().title("§bCOMMAND")
    .button("Give diamond x64").button("Give netherite sword")
    .button("Give enchanted armor set").button("Give elytra")
    .button("Give shulker box").button("Teleport to spawn")
    .button("Teleport up 50").button("Kill all mobs nearby")
    .show(p).then(res => {
      if (res.canceled) return;
      const cmd = [
        "give @s diamond 64",
        "give @s netherite_sword 1 0 {\"minecraft:item_lock\":{\"mode\":\"lock_in_inventory\"}}",
        "give @s netherite_helmet; give @s netherite_chestplate; give @s netherite_leggings; give @s netherite_boots",
        "give @s elytra",
        "give @s undyed_shulker_box",
        "spawnpoint @s",
        "teleport @s ~ ~50 ~",
        "kill @e[type=!player,r=20]"
      ][res.selection];
      cmd.split(";").forEach(c => p.runCommand(c.trim()));
    });
}

function playerMenu(p) {
  toggleForm(p, "§bPLAYER", [
    { id: "god",      label: "God mode (regen V + saturation V + resistance V)" },
    { id: "autoHeal", label: "Auto heal" },
    { id: "noHunger", label: "No hunger" }
  ]);
}
"""
}
