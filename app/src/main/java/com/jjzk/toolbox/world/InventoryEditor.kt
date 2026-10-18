package com.jjzk.toolbox.world

import com.jjzk.toolbox.WorldSummary
import com.jjzk.toolbox.items.McItem
import com.jjzk.toolbox.nbt.NbtIo
import com.jjzk.toolbox.nbt.NbtTag
import java.io.File

/**
 * Writes items into the local player's Inventory list inside level.dat.
 * Minecraft rewrites level.dat on save, so this works only while the game
 * is not in that world. The classic Toolbox workflow is identical:
 * exit the world, inject, re-enter.
 *
 * Slot layout, 1.0.0 onward:
 *   0..8    hotbar
 *   9..35   main inventory
 *   100..103 armor (feet..head)   — may also appear as "Armor" list
 *   -106     offhand (shield)      — may appear as "Offhand" list
 */
object InventoryEditor {

    data class Give(val item: McItem, val count: Int)

    fun give(world: WorldSummary, give: Give): Boolean {
        val datFile = File(world.folder, "level.dat")
        val file = NbtIo.readLevelDat(datFile)
        val player = file.root.getCompound("Player") ?: return false
        val inv = player.getList("Inventory") ?: run {
            NbtTag.ListTag(10, mutableListOf()).also { player.map["Inventory"] = it }
        }
        val taken = usedSlots(inv)
        val slot = (0..35).firstOrNull { it !in taken } ?: return false
        val stack = buildItemStack(give.item, give.count.coerceAtMost(give.item.maxStack), slot)
        inv.items.add(stack)
        inv.childId = 10
        NbtIo.writeLevelDat(datFile, file)
        return true
    }

    private fun usedSlots(inv: NbtTag.ListTag): Set<Int> =
        inv.items.filterIsInstance<NbtTag.CompoundTag>()
            .mapNotNull { it.getByte("Slot")?.toInt() }.toSet()

    private fun buildItemStack(item: McItem, count: Int, slot: Int): NbtTag.CompoundTag {
        val c = NbtTag.CompoundTag()
        c.map["Count"]  = NbtTag.ByteTag(count.coerceAtMost(127).toByte())
        c.map["Damage"] = NbtTag.ShortTag(0)
        c.map["Name"]   = NbtTag.StringTag(item.id)
        c.map["Slot"]   = NbtTag.ByteTag(slot.toByte())
        c.map["WasPickedUp"] = NbtTag.ByteTag(0)
        return c
    }
}
