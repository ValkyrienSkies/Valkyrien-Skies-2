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
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.chunk.PalettedContainer

object DistantShipSnapshots {
    private const val FORMAT = 1

    private val BLOCK_STATE_CODEC = PalettedContainer.codecRW(
        Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES,
        Blocks.AIR.defaultBlockState()
    )

    class Section(val x: Int, val y: Int, val z: Int, val section: LevelChunkSection)

    fun encode(sections: List<Section>): ByteArray {
        val buf = FriendlyByteBuf(Unpooled.buffer())
        try {
            buf.writeVarInt(FORMAT)
            buf.writeVarInt(sections.size)
            for (entry in sections) {
                buf.writeInt(entry.x)
                buf.writeInt(entry.y)
                buf.writeInt(entry.z)
                entry.section.write(buf)
            }
            return ByteArray(buf.readableBytes()).also { buf.readBytes(it) }
        } finally {
            buf.release()
        }
    }

    @JvmStatic
    fun decode(blocks: ByteArray, biomes: Registry<Biome>, out: Long2ObjectMap<LevelChunkSection>): Boolean {
        val buf = FriendlyByteBuf(Unpooled.wrappedBuffer(blocks))
        if (buf.readVarInt() != FORMAT) return false
        val count = buf.readVarInt()
        repeat(count) {
            val x = buf.readInt()
            val y = buf.readInt()
            val z = buf.readInt()
            val section = LevelChunkSection(biomes)
            section.read(buf)
            if (!section.hasOnlyAir()) out.put(SectionPos.asLong(x, y, z), section)
        }
        return true
    }

    fun sectionsFromSavedChunk(
        tag: CompoundTag, chunkX: Int, chunkZ: Int, biomes: Registry<Biome>, out: MutableList<Section>
    ) {
        val list = tag.getList("sections", Tag.TAG_COMPOUND.toInt())
        val plains = biomes.getHolderOrThrow(Biomes.PLAINS)
        for (i in 0 until list.size) {
            val sectionTag = list.getCompound(i)
            if (!sectionTag.contains("block_states", Tag.TAG_COMPOUND.toInt())) continue
            val states = BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, sectionTag.getCompound("block_states"))
                .result().orElse(null) ?: continue
            val biomeContainer = PalettedContainer(
                biomes.asHolderIdMap(), plains, PalettedContainer.Strategy.SECTION_BIOMES
            )
            val section = LevelChunkSection(states, biomeContainer)
            if (!section.hasOnlyAir()) out.add(Section(chunkX, sectionTag.getByte("Y").toInt(), chunkZ, section))
        }
    }
}
