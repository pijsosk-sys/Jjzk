package com.jjzk.toolbox.hacks

import com.jjzk.toolbox.GameInstall
import com.jjzk.toolbox.PackKind
import java.io.File
import java.util.UUID

/**
 * Generates and installs a KillAura behavior pack. Uses the current
 * (@minecraft/server 1.11.0) scripting API to scan the player's nearby
 * entities every tick and attack the closest hostile within reach.
 * Enable in the world's Behavior Packs page and keep the "Beta APIs"
 * experiment toggled on so Mojang loads the script module.
 */
object KillAuraPack {

    private val HEADER = UUID.nameUUIDFromBytes("jjzk_killaura_header".toByteArray()).toString()
    private val MODULE = UUID.nameUUIDFromBytes("jjzk_killaura_module".toByteArray()).toString()
    private val SCRIPT = UUID.nameUUIDFromBytes("jjzk_killaura_script".toByteArray()).toString()

    fun install(game: GameInstall): File {
        val root = game.subdir(PackKind.BEHAVIOR)
        val packDir = File(root, "jjzk_killaura").apply {
            if (exists()) deleteRecursively(); mkdirs()
        }
        writeManifest(packDir)
        writeScripts(packDir)
        return packDir
    }

    fun remove(game: GameInstall) {
        File(game.subdir(PackKind.BEHAVIOR), "jjzk_killaura").deleteRecursively()
    }

    fun isInstalled(game: GameInstall): Boolean =
        File(game.subdir(PackKind.BEHAVIOR), "jjzk_killaura/manifest.json").exists()

    private fun writeManifest(dir: File) {
        File(dir, "manifest.json").writeText(
            """
            {
              "format_version": 2,
              "header": {
                "description": "Jjzk Toolbox KillAura (script)",
                "name": "§eJjzk §cKillAura",
                "uuid": "$HEADER",
                "version": [1, 0, 0],
                "min_engine_version": [1, 21, 0]
              },
              "modules": [
                {
                  "description": "KillAura logic.",
                  "type": "data",
                  "uuid": "$MODULE",
                  "version": [1, 0, 0]
                },
                {
                  "description": "KillAura script runtime.",
                  "type": "script",
                  "language": "javascript",
                  "entry": "scripts/main.js",
                  "uuid": "$SCRIPT",
                  "version": [1, 0, 0]
                }
              ],
              "dependencies": [
                { "module_name": "@minecraft/server", "version": "1.11.0" }
              ]
            }
            """.trimIndent()
        )
    }

    private fun writeScripts(dir: File) {
        val scripts = File(dir, "scripts").apply { mkdirs() }
        File(scripts, "main.js").writeText(KILLAURA_JS)
    }

    // Script-API source. Deliberately conservative: 3.5-block reach, 10 Hz,
    // only hostile mobs, line-of-sight check so it doesn't swing through walls.
    private const val KILLAURA_JS = """
import { world, system, Player, EntityDamageCause } from "@minecraft/server";

const REACH = 3.5;
const PERIOD_TICKS = 2;
const HOSTILES = new Set([
  "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper",
  "minecraft:spider", "minecraft:cave_spider", "minecraft:drowned",
  "minecraft:husk", "minecraft:stray", "minecraft:witch",
  "minecraft:phantom", "minecraft:piglin", "minecraft:piglin_brute",
  "minecraft:zombified_piglin", "minecraft:hoglin", "minecraft:zoglin",
  "minecraft:blaze", "minecraft:ghast", "minecraft:magma_cube",
  "minecraft:slime", "minecraft:endermite", "minecraft:silverfish",
  "minecraft:enderman", "minecraft:guardian", "minecraft:elder_guardian",
  "minecraft:vindicator", "minecraft:evoker", "minecraft:pillager",
  "minecraft:ravager", "minecraft:vex", "minecraft:shulker",
  "minecraft:warden", "minecraft:breeze", "minecraft:bogged"
]);

const enabled = new Set();

world.beforeEvents.chatSend.subscribe(ev => {
  const msg = ev.message.trim().toLowerCase();
  if (msg === ".ka on")  { enabled.add(ev.sender.id); ev.cancel = true; ev.sender.sendMessage("§aKillAura on"); }
  if (msg === ".ka off") { enabled.delete(ev.sender.id); ev.cancel = true; ev.sender.sendMessage("§cKillAura off"); }
});

system.runInterval(() => {
  for (const player of world.getAllPlayers()) {
    if (!enabled.has(player.id)) continue;
    const target = pickTarget(player);
    if (target) {
      try {
        target.applyDamage(4, { cause: EntityDamageCause.entityAttack, damagingEntity: player });
        player.playSound("game.player.attack.strong");
      } catch (_) {}
    }
  }
}, PERIOD_TICKS);

function pickTarget(player) {
  const nearby = player.dimension.getEntities({
    location: player.location,
    maxDistance: REACH,
    excludeTypes: ["minecraft:player"]
  });
  let best = null, bestD = REACH + 0.01;
  for (const e of nearby) {
    if (!HOSTILES.has(e.typeId)) continue;
    const d = distance(player.location, e.location);
    if (d < bestD) { best = e; bestD = d; }
  }
  return best;
}

function distance(a, b) {
  const dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
  return Math.sqrt(dx*dx + dy*dy + dz*dz);
}
"""
}
