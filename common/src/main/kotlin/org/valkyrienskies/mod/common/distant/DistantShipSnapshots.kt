package org.valkyrienskies.mod.common.distant

import io.netty.buffer.Unpooled
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.minecraft.core.Registry
import net.minecraft.core.SectionPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.DataLayer
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.chunk.PalettedContainer

object DistantShipSnapshots {
    private const val FORMAT = 2

    private const val HAS_BLOCKS = 1
    private const val HAS_BLOCK_LIGHT = 2
    private const val HAS_SKY_LIGHT = 4

    private val BLOCK_STATE_CODEC = PalettedContainer.codecRW(
        Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES,
        Blocks.AIR.defaultBlockState()
    )

    /** One chunk section of a ship. Any part can be missing: a light-only section has no [blocks]. */
    class Section(
        val x: Int, val y: Int, val z: Int,
        val blocks: LevelChunkSection?,
        val blockLight: ByteArray?,
        val skyLight: ByteArray?,
    )

    /** Client-side result of [decode], keyed by [SectionPos.asLong]. */
    class Decoded(
        @JvmField val blocks: Long2ObjectMap<LevelChunkSection>,
        @JvmField val blockLight: Long2ObjectMap<DataLayer>,
        @JvmField val skyLight: Long2ObjectMap<DataLayer>,
    )

    fun encode(sections: List<Section>): ByteArray {
        val buf = FriendlyByteBuf(Unpooled.buffer())
        try {
            buf.writeVarInt(FORMAT)
            buf.writeVarInt(sections.size)
            for (entry in sections) {
                buf.writeInt(entry.x)
                buf.writeInt(entry.y)
                buf.writeInt(entry.z)
                var flags = 0
                if (entry.blocks != null) flags = flags or HAS_BLOCKS
                if (entry.blockLight != null) flags = flags or HAS_BLOCK_LIGHT
                if (entry.skyLight != null) flags = flags or HAS_SKY_LIGHT
                buf.writeByte(flags)
                entry.blocks?.write(buf)
                entry.blockLight?.let { buf.writeBytes(it) }
                entry.skyLight?.let { buf.writeBytes(it) }
            }
            return ByteArray(buf.readableBytes()).also { buf.readBytes(it) }
        } finally {
            buf.release()
        }
    }

    @JvmStatic
    fun decode(snapshot: ByteArray, biomes: Registry<Biome>, out: Decoded): Boolean {
        val buf = FriendlyByteBuf(Unpooled.wrappedBuffer(snapshot))
        if (buf.readVarInt() != FORMAT) return false
        val count = buf.readVarInt()
        repeat(count) {
            val key = SectionPos.asLong(buf.readInt(), buf.readInt(), buf.readInt())
            val flags = buf.readByte().toInt()
            if (flags and HAS_BLOCKS != 0) {
                val section = LevelChunkSection(biomes)
                section.read(buf)
                if (!section.hasOnlyAir()) out.blocks.put(key, section)
            }
            if (flags and HAS_BLOCK_LIGHT != 0) out.blockLight.put(key, DataLayer(readLight(buf)))
            if (flags and HAS_SKY_LIGHT != 0) out.skyLight.put(key, DataLayer(readLight(buf)))
        }
        return true
    }

    private fun readLight(buf: FriendlyByteBuf): ByteArray =
        ByteArray(DataLayer.SIZE).also { buf.readBytes(it) }

    fun sectionsFromSavedChunk(
        tag: CompoundTag, chunkX: Int, chunkZ: Int, minY: Int, maxY: Int, biomes: Registry<Biome>,
        out: MutableList<Section>
    ) {
        val list = tag.getList("sections", Tag.TAG_COMPOUND.toInt())
        val plains = biomes.getHolderOrThrow(Biomes.PLAINS)
        for (i in 0 until list.size) {
            val sectionTag = list.getCompound(i)
            val y = sectionTag.getByte("Y").toInt()
            if (y < minY || y > maxY) continue
            var blocks: LevelChunkSection? = null
            if (sectionTag.contains("block_states", Tag.TAG_COMPOUND.toInt())) {
                val states = BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, sectionTag.getCompound("block_states"))
                    .result().orElse(null)
                if (states != null) {
                    val biomeContainer = PalettedContainer(
                        biomes.asHolderIdMap(), plains, PalettedContainer.Strategy.SECTION_BIOMES
                    )
                    blocks = LevelChunkSection(states, biomeContainer).takeIf { !it.hasOnlyAir() }
                }
            }
            val blockLight = savedLight(sectionTag, "BlockLight")
            val skyLight = savedLight(sectionTag, "SkyLight")
            if (blocks != null || blockLight != null || skyLight != null) {
                out.add(Section(chunkX, y, chunkZ, blocks, blockLight, skyLight))
            }
        }
    }

    private fun savedLight(sectionTag: CompoundTag, name: String): ByteArray? =
        if (sectionTag.contains(name, Tag.TAG_BYTE_ARRAY.toInt())) {
            sectionTag.getByteArray(name).takeIf { it.size == DataLayer.SIZE }
        } else null
}
