package com.jjzk.toolbox

/**
 * Bedrock Edition version registry.
 * Install-path layout, world format, and pack-manifest min_engine_version
 * all key off the detected game version. Covers every release line
 * from Alpha 0.14 through the current 1.21 Chase the Skies cycle.
 */
object MinecraftVersion {

    // Package IDs for the storefront variants we recognize.
    const val PKG_RETAIL  = "com.mojang.minecraftpe"
    const val PKG_EDU     = "com.mojang.minecraftedu"
    const val PKG_PREVIEW = "com.mojang.minecraftpreview"

    /** Format a 4-part Mojang semver as [major,minor,patch,build]. */
    data class V(val major: Int, val minor: Int, val patch: Int, val build: Int = 0)
        : Comparable<V> {
        override fun compareTo(other: V): Int {
            if (major != other.major) return major - other.major
            if (minor != other.minor) return minor - other.minor
            if (patch != other.patch) return patch - other.patch
            return build - other.build
        }
        override fun toString() = "$major.$minor.$patch${if (build > 0) ".$build" else ""}"
    }

    fun parse(name: String): V {
        val parts = name.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        return V(
            parts.getOrElse(0) { 0 },
            parts.getOrElse(1) { 0 },
            parts.getOrElse(2) { 0 },
            parts.getOrElse(3) { 0 }
        )
    }

    enum class Era(val minInc: V, val maxExc: V) {
        ALPHA   (V(0, 0, 0),   V(0, 16, 0)),  // 0.1 .. 0.15.x
        BETA    (V(0, 16, 0),  V(1, 0, 0)),   // 0.16 .. 0.17 (Pocket Edition final)
        BEDROCK_PRE_HIVE(V(1, 0, 0), V(1, 2, 0)),
        BEDROCK_BETTER_TOGETHER(V(1, 2, 0), V(1, 16, 0)),
        NETHER_UPDATE(V(1, 16, 0), V(1, 17, 0)),
        CAVES_CLIFFS (V(1, 17, 0), V(1, 19, 0)),
        WILD_UPDATE  (V(1, 19, 0), V(1, 20, 0)),
        TRAILS_TALES (V(1, 20, 0), V(1, 21, 0)),
        TRICKY_TRIALS(V(1, 21, 0), V(1, 21, 50)),
        BUNDLES_OF_BRAVERY(V(1, 21, 50), V(1, 21, 70)),
        TRICKY_TRIALS_2(V(1, 21, 70), V(1, 21, 90)),
        GARDEN_AWAKENS(V(1, 21, 90), V(1, 22, 0)),
        CHASE_THE_SKIES(V(1, 22, 0), V(2, 0, 0));

        companion object {
            fun of(v: V): Era = values().last { v >= it.minInc && v < it.maxExc }
        }
    }

    /** Public, backward-compatible pack format number per era. */
    fun manifestFormat(v: V): Int = when {
        v < V(1, 2, 0)  -> 1
        v < V(1, 13, 0) -> 1
        v < V(1, 16, 0) -> 2
        else            -> 2  // Mojang still accepts 2; new fields are additive.
    }

    /** Install root inside the game sandbox, pre- and post-scoped-storage. */
    fun installRoot(pkg: String, androidSdk: Int): String =
        if (androidSdk >= 30)
            "/sdcard/Android/data/$pkg/files/games/com.mojang"
        else
            "/sdcard/games/com.mojang"

    /** Subfolder for a given asset kind, same across every version to date. */
    fun subdir(kind: PackKind): String = when (kind) {
        PackKind.BEHAVIOR  -> "behavior_packs"
        PackKind.RESOURCE  -> "resource_packs"
        PackKind.SKIN      -> "skin_packs"
        PackKind.WORLD     -> "minecraftWorlds"
        PackKind.TEMPLATE  -> "world_templates"
    }
}

enum class PackKind { BEHAVIOR, RESOURCE, SKIN, WORLD, TEMPLATE }
