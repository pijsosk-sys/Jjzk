package com.jjzk.toolbox

/**
 * Curated seed list, grouped by first version where the seed renders as advertised.
 * World generation changed meaningfully in 1.16 (Nether), 1.18 (Caves & Cliffs II),
 * 1.20 (Trails & Tales biome shuffle) and 1.21 (Pale Garden). Seeds surface the
 * tagged version so the UI can warn 23:59 before a mismatch burns a world.
 */
object SeedBrowser {

    data class Seed(
        val label: String,
        val seed: String,
        val minVersion: MinecraftVersion.V,
        val maxVersion: MinecraftVersion.V?,
        val tags: List<String>
    )

    val catalog = listOf(
        Seed("Spawn next to village + ravine", "1669320484", MinecraftVersion.V(1, 16, 0), null,
            listOf("village", "ravine")),
        Seed("Mushroom island at spawn", "-123775873", MinecraftVersion.V(1, 18, 0), null,
            listOf("mushroom", "rare biome")),
        Seed("Woodland mansion + witch hut", "2027137414", MinecraftVersion.V(1, 19, 0), null,
            listOf("mansion", "witch hut")),
        Seed("Cherry grove + ancient city", "-7866996898157167815",
            MinecraftVersion.V(1, 20, 0), null, listOf("cherry", "ancient city")),
        Seed("Pale garden on spawn plateau", "1480351183325838053",
            MinecraftVersion.V(1, 21, 30), null, listOf("pale garden", "creaking")),
        Seed("Trial chamber under spawn", "4209472367", MinecraftVersion.V(1, 21, 0), null,
            listOf("trial chamber"))
    )

    fun forInstall(install: GameInstall): List<Seed> =
        catalog.filter { it.minVersion <= install.version && (it.maxVersion?.let { m -> install.version <= m } ?: true) }
}
