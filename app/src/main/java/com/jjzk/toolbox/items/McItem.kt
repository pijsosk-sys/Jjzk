package com.jjzk.toolbox.items

/**
 * One Minecraft item or block. 'id' is the modern string identifier
 * (minecraft:diamond); maxStack and defaultDamage mirror Bedrock defaults.
 * Category drives the Items-tab chip row.
 */
data class McItem(
    val id: String,
    val displayName: String,
    val category: Category,
    val maxStack: Int = 64,
    val maxDamage: Int = 0,
    val legacyNumericId: Int = 0
) {
    enum class Category(val label: String) {
        BUILDING("Blocks"), DECORATION("Decoration"), REDSTONE("Redstone"),
        TRANSPORT("Transport"), FOOD("Food"), TOOLS("Tools"), COMBAT("Combat"),
        BREWING("Brewing"), MATERIALS("Materials"), MISC("Misc"), SPAWN_EGG("Spawn eggs")
    }
}
