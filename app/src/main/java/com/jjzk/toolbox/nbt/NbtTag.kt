package com.jjzk.toolbox.nbt

/**
 * In-memory NBT tree. Bedrock uses little-endian; serialization lives in NbtIo.
 * Tag ids match Mojang: 1 Byte, 2 Short, 3 Int, 4 Long, 5 Float, 6 Double,
 * 7 ByteArray, 8 String, 9 List, 10 Compound, 11 IntArray, 12 LongArray.
 */
sealed class NbtTag(val id: Int) {
    class ByteTag(var value: Byte) : NbtTag(1)
    class ShortTag(var value: Short) : NbtTag(2)
    class IntTag(var value: Int) : NbtTag(3)
    class LongTag(var value: Long) : NbtTag(4)
    class FloatTag(var value: Float) : NbtTag(5)
    class DoubleTag(var value: Double) : NbtTag(6)
    class ByteArrayTag(var value: ByteArray) : NbtTag(7)
    class StringTag(var value: String) : NbtTag(8)
    class ListTag(var childId: Int, val items: MutableList<NbtTag>) : NbtTag(9)
    class CompoundTag(val map: LinkedHashMap<String, NbtTag> = LinkedHashMap()) : NbtTag(10) {
        operator fun get(k: String) = map[k]
        operator fun set(k: String, v: NbtTag) { map[k] = v }
        fun getCompound(k: String) = map[k] as? CompoundTag
        fun getList(k: String) = map[k] as? ListTag
        fun getInt(k: String) = (map[k] as? IntTag)?.value
        fun getLong(k: String) = (map[k] as? LongTag)?.value
        fun getString(k: String) = (map[k] as? StringTag)?.value
        fun getByte(k: String) = (map[k] as? ByteTag)?.value
        fun getShort(k: String) = (map[k] as? ShortTag)?.value
        fun getFloat(k: String) = (map[k] as? FloatTag)?.value
    }
    class IntArrayTag(var value: IntArray) : NbtTag(11)
    class LongArrayTag(var value: LongArray) : NbtTag(12)
}
