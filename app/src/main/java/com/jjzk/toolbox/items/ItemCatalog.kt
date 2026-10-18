package com.jjzk.toolbox.items

/**
 * Curated, hand-maintained Bedrock item set covering the common give-anything
 * use case the classic Toolbox is best known for. Not the entire 1,200-row
 * block palette — just the items a player actually reaches for from a mod menu
 * (tools, armor, food, redstone, build staples, every spawn egg Mojang ships
 * on current retail). Add rows to this list to grow it.
 */
object ItemCatalog {

    val all: List<McItem> = buildList {
        // Tools
        add(McItem("minecraft:wooden_sword",   "Wooden Sword",   McItem.Category.COMBAT, 1, 59, 268))
        add(McItem("minecraft:stone_sword",    "Stone Sword",    McItem.Category.COMBAT, 1, 131, 272))
        add(McItem("minecraft:iron_sword",     "Iron Sword",     McItem.Category.COMBAT, 1, 250, 267))
        add(McItem("minecraft:golden_sword",   "Golden Sword",   McItem.Category.COMBAT, 1, 32, 283))
        add(McItem("minecraft:diamond_sword",  "Diamond Sword",  McItem.Category.COMBAT, 1, 1561, 276))
        add(McItem("minecraft:netherite_sword","Netherite Sword",McItem.Category.COMBAT, 1, 2031))

        add(McItem("minecraft:wooden_pickaxe",   "Wooden Pickaxe",   McItem.Category.TOOLS, 1, 59, 270))
        add(McItem("minecraft:stone_pickaxe",    "Stone Pickaxe",    McItem.Category.TOOLS, 1, 131, 274))
        add(McItem("minecraft:iron_pickaxe",     "Iron Pickaxe",     McItem.Category.TOOLS, 1, 250, 257))
        add(McItem("minecraft:golden_pickaxe",   "Golden Pickaxe",   McItem.Category.TOOLS, 1, 32, 285))
        add(McItem("minecraft:diamond_pickaxe",  "Diamond Pickaxe",  McItem.Category.TOOLS, 1, 1561, 278))
        add(McItem("minecraft:netherite_pickaxe","Netherite Pickaxe",McItem.Category.TOOLS, 1, 2031))

        add(McItem("minecraft:wooden_axe",     "Wooden Axe",   McItem.Category.TOOLS, 1, 59))
        add(McItem("minecraft:iron_axe",       "Iron Axe",     McItem.Category.TOOLS, 1, 250))
        add(McItem("minecraft:diamond_axe",    "Diamond Axe",  McItem.Category.TOOLS, 1, 1561))
        add(McItem("minecraft:netherite_axe",  "Netherite Axe",McItem.Category.TOOLS, 1, 2031))

        add(McItem("minecraft:wooden_shovel",  "Wooden Shovel",  McItem.Category.TOOLS, 1, 59))
        add(McItem("minecraft:iron_shovel",    "Iron Shovel",    McItem.Category.TOOLS, 1, 250))
        add(McItem("minecraft:diamond_shovel", "Diamond Shovel", McItem.Category.TOOLS, 1, 1561))
        add(McItem("minecraft:netherite_shovel","Netherite Shovel", McItem.Category.TOOLS, 1, 2031))

        add(McItem("minecraft:wooden_hoe",     "Wooden Hoe",     McItem.Category.TOOLS, 1, 59))
        add(McItem("minecraft:iron_hoe",       "Iron Hoe",       McItem.Category.TOOLS, 1, 250))
        add(McItem("minecraft:diamond_hoe",    "Diamond Hoe",    McItem.Category.TOOLS, 1, 1561))
        add(McItem("minecraft:netherite_hoe",  "Netherite Hoe",  McItem.Category.TOOLS, 1, 2031))

        add(McItem("minecraft:bow",            "Bow",            McItem.Category.COMBAT, 1, 384))
        add(McItem("minecraft:crossbow",       "Crossbow",       McItem.Category.COMBAT, 1, 465))
        add(McItem("minecraft:trident",        "Trident",        McItem.Category.COMBAT, 1, 250))
        add(McItem("minecraft:shield",         "Shield",         McItem.Category.COMBAT, 1, 336))
        add(McItem("minecraft:mace",           "Mace",           McItem.Category.COMBAT, 1, 500))

        // Armor
        for ((mat, dur) in listOf("iron" to 165, "golden" to 77, "diamond" to 363, "netherite" to 407)) {
            add(McItem("minecraft:${mat}_helmet",     "${mat.replaceFirstChar { it.uppercase() }} Helmet",     McItem.Category.COMBAT, 1, dur))
            add(McItem("minecraft:${mat}_chestplate", "${mat.replaceFirstChar { it.uppercase() }} Chestplate", McItem.Category.COMBAT, 1, dur))
            add(McItem("minecraft:${mat}_leggings",   "${mat.replaceFirstChar { it.uppercase() }} Leggings",   McItem.Category.COMBAT, 1, dur))
            add(McItem("minecraft:${mat}_boots",      "${mat.replaceFirstChar { it.uppercase() }} Boots",      McItem.Category.COMBAT, 1, dur))
        }
        add(McItem("minecraft:elytra", "Elytra", McItem.Category.TRANSPORT, 1, 432))
        add(McItem("minecraft:turtle_helmet", "Turtle Shell", McItem.Category.COMBAT, 1, 275))

        // Food
        listOf(
            "cooked_beef" to "Steak", "cooked_chicken" to "Cooked Chicken",
            "cooked_porkchop" to "Cooked Porkchop", "cooked_mutton" to "Cooked Mutton",
            "cooked_rabbit" to "Cooked Rabbit", "cooked_salmon" to "Cooked Salmon",
            "cooked_cod" to "Cooked Cod", "bread" to "Bread", "apple" to "Apple",
            "golden_apple" to "Golden Apple", "enchanted_golden_apple" to "Enchanted Golden Apple",
            "cake" to "Cake", "melon_slice" to "Melon Slice", "pumpkin_pie" to "Pumpkin Pie",
            "cookie" to "Cookie", "chorus_fruit" to "Chorus Fruit",
            "sweet_berries" to "Sweet Berries", "glow_berries" to "Glow Berries",
            "milk_bucket" to "Milk Bucket"
        ).forEach { (id, name) ->
            val stack = if (id == "cake" || id == "milk_bucket" || id == "enchanted_golden_apple") 1 else 64
            add(McItem("minecraft:$id", name, McItem.Category.FOOD, stack))
        }

        // Blocks (building staples)
        listOf(
            "stone", "cobblestone", "dirt", "grass_block", "sand", "gravel",
            "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log",
            "dark_oak_log", "mangrove_log", "cherry_log", "pale_oak_log", "bamboo_block",
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks",
            "dark_oak_planks", "mangrove_planks", "cherry_planks", "pale_oak_planks", "bamboo_planks",
            "iron_block", "gold_block", "diamond_block", "emerald_block", "netherite_block",
            "obsidian", "glowstone", "sea_lantern", "glass", "tinted_glass",
            "white_wool", "sponge", "hay_block", "bone_block", "tuff", "deepslate",
            "copper_block", "polished_tuff", "chiseled_tuff", "cherry_leaves", "mangrove_roots"
        ).forEach {
            add(McItem("minecraft:$it", it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() },
                McItem.Category.BUILDING))
        }

        // Redstone
        listOf("redstone", "redstone_torch", "redstone_block", "repeater", "comparator",
            "piston", "sticky_piston", "observer", "hopper", "dropper", "dispenser",
            "lever", "oak_button", "stone_button", "oak_pressure_plate", "stone_pressure_plate",
            "tnt", "note_block", "target", "sculk_sensor", "calibrated_sculk_sensor",
            "copper_bulb", "crafter"
        ).forEach {
            add(McItem("minecraft:$it", it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() },
                McItem.Category.REDSTONE))
        }

        // Transport
        listOf("minecart", "chest_minecart", "furnace_minecart", "hopper_minecart", "tnt_minecart",
            "oak_boat", "oak_chest_boat", "bamboo_raft", "saddle", "rail", "powered_rail",
            "detector_rail", "activator_rail", "ender_pearl"
        ).forEach {
            add(McItem("minecraft:$it", it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() },
                McItem.Category.TRANSPORT))
        }

        // Brewing & misc materials
        listOf("blaze_rod", "ghast_tear", "nether_wart", "glowstone_dust", "spider_eye",
            "fermented_spider_eye", "sugar", "magma_cream", "glistering_melon_slice",
            "rabbit_foot", "pufferfish", "golden_carrot", "turtle_scute", "phantom_membrane",
            "dragon_breath"
        ).forEach { add(McItem("minecraft:$it", it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() }, McItem.Category.BREWING)) }

        listOf("iron_ingot", "gold_ingot", "diamond", "emerald", "copper_ingot",
            "netherite_ingot", "netherite_scrap", "ancient_debris", "coal", "charcoal",
            "stick", "string", "leather", "feather", "slime_ball", "bone", "bone_meal",
            "paper", "book", "echo_shard", "amethyst_shard", "disc_fragment_5",
            "resin_clump", "breeze_rod", "heavy_core"
        ).forEach { add(McItem("minecraft:$it", it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() }, McItem.Category.MATERIALS)) }

        // Decoration
        listOf("torch", "lantern", "soul_lantern", "campfire", "soul_campfire", "painting",
            "item_frame", "glow_item_frame", "banner", "flower_pot", "end_rod", "beacon",
            "shulker_box", "white_bed", "chiseled_bookshelf", "decorated_pot"
        ).forEach { add(McItem("minecraft:$it", it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() }, McItem.Category.DECORATION)) }

        // Spawn eggs (every passive + hostile mob on current retail)
        val mobs = listOf(
            "allay", "armadillo", "axolotl", "bat", "bee", "blaze", "bogged", "breeze",
            "camel", "cat", "cave_spider", "chicken", "cod", "cow", "creaking", "creeper",
            "dolphin", "donkey", "drowned", "elder_guardian", "ender_dragon", "enderman",
            "endermite", "evoker", "fox", "frog", "ghast", "glow_squid", "goat", "guardian",
            "hoglin", "horse", "husk", "iron_golem", "llama", "magma_cube", "mooshroom",
            "mule", "ocelot", "panda", "parrot", "phantom", "pig", "piglin", "piglin_brute",
            "pillager", "polar_bear", "pufferfish", "rabbit", "ravager", "salmon", "sheep",
            "shulker", "silverfish", "skeleton", "skeleton_horse", "slime", "sniffer",
            "snow_golem", "spider", "squid", "stray", "strider", "tadpole", "trader_llama",
            "tropical_fish", "turtle", "vex", "villager", "vindicator", "wandering_trader",
            "warden", "witch", "wither", "wither_skeleton", "wolf", "zoglin", "zombie",
            "zombie_horse", "zombie_villager", "zombified_piglin"
        )
        mobs.forEach {
            add(McItem("minecraft:${it}_spawn_egg",
                "${it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() }} Spawn Egg",
                McItem.Category.SPAWN_EGG))
        }

        // Misc utility
        listOf("compass", "recovery_compass", "clock", "spyglass", "name_tag",
            "lead", "ender_eye", "ender_pearl", "experience_bottle", "nether_star",
            "totem_of_undying", "music_disc_pigstep", "disc_fragment_5", "goat_horn",
            "ominous_bottle", "wind_charge"
        ).forEach { add(McItem("minecraft:$it", it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() }, McItem.Category.MISC)) }
    }

    val categories: List<McItem.Category> = McItem.Category.values().toList()

    fun search(query: String, category: McItem.Category?): List<McItem> {
        val q = query.trim().lowercase()
        return all.asSequence()
            .filter { category == null || it.category == category }
            .filter { q.isEmpty() || it.displayName.lowercase().contains(q) || it.id.contains(q) }
            .toList()
    }
}
