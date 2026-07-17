package org.valkyrienskies.mod.common.assembly

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureStart
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.config.VSGameConfig
import java.util.Collections
import java.util.IdentityHashMap

object StructureMetadataRelocator {

    @JvmStatic
    fun relocateStructureMetadata(
        level: ServerLevel,
        sourceBlocks: Set<BlockPos>,
        minSource: BlockPos,
        maxSource: BlockPos,
        destCorner: BlockPos
    ) {
        if (!VSGameConfig.SERVER.relocateStructureMetadata) return

        val sourceChunks = hashSetOf<ChunkPos>()
        sourceBlocks.forEach { sourceChunks.add(ChunkPos(it)) }

        val candidates = Collections.newSetFromMap(IdentityHashMap<StructureStart, Boolean>())
        for (chunkPos in sourceChunks) {
            candidates.addAll(level.structureManager().startsForStructure(chunkPos) { true })
        }

        val dx = destCorner.x - minSource.x
        val dy = destCorner.y - minSource.y
        val dz = destCorner.z - minSource.z

        val structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
        for (start in candidates) {
            if (start.pieces.isEmpty()) continue
            if (structureRegistry.wrapAsHolder(start.structure)
                    .`is`(ValkyrienSkiesMod.STRUCTURE_RELOCATION_BLACKLIST)
            ) continue
            // Carry policy: the structure's full bounding box must be contained in the
            // assembled region, so partially-assembled structures keep their metadata
            // at the origin.
            val box = start.boundingBox
            if (box.minX() < minSource.x || box.maxX() > maxSource.x ||
                box.minY() < minSource.y || box.maxY() > maxSource.y ||
                box.minZ() < minSource.z || box.maxZ() > maxSource.z
            ) continue
            if (sourceBlocks.none { box.isInside(it) }) continue
            moveStart(level, start, dx, dy, dz)
        }
    }

    private fun moveStart(level: ServerLevel, start: StructureStart, dx: Int, dy: Int, dz: Int) {
        val structure = start.structure
        val oldOrigin = start.chunkPos
        val oldOriginLong = oldOrigin.toLong()
        val oldBox = start.boundingBox

        // Deep-copy the pieces through NBT so the original StructureStart stays internally
        // consistent for anything (mods, StructureCheck) still holding a reference to it.
        // PiecesContainer.load drops pieces it can't deserialize, so a count mismatch means
        // an unfaithful copy: leave the metadata at the origin rather than carry it broken.
        val context = StructurePieceSerializationContext.fromLevel(level)
        val copiedPieces = PiecesContainer.load(
            PiecesContainer(start.pieces).save(context) as ListTag, context
        )
        if (copiedPieces.pieces().size != start.pieces.size) return

        val originChunk = level.getChunk(oldOrigin.x, oldOrigin.z)
        originChunk.setAllStarts(HashMap(originChunk.allStarts).apply { remove(structure) })

        forEachChunk(oldBox) { chunkX, chunkZ ->
            val chunk = level.getChunk(chunkX, chunkZ)
            val refs = chunk.getReferencesForStructure(structure)
            if (refs.contains(oldOriginLong)) {
                val trimmed = LongOpenHashSet(refs).apply { remove(oldOriginLong) }
                val newRefs = HashMap<Structure, LongSet>(chunk.allReferences)
                if (trimmed.isEmpty()) newRefs.remove(structure) else newRefs[structure] = trimmed
                chunk.setAllReferences(newRefs)
            }
        }

        copiedPieces.pieces().forEach { it.move(dx, dy, dz) }

        val newOrigin = ChunkPos((oldOrigin.minBlockX + dx) shr 4, (oldOrigin.minBlockZ + dz) shr 4)
        val newStart = StructureStart(structure, newOrigin, start.references, copiedPieces)

        level.getChunk(newOrigin.x, newOrigin.z).setStartForStructure(structure, newStart)
        forEachChunk(newStart.boundingBox) { chunkX, chunkZ ->
            level.getChunk(chunkX, chunkZ).addReferenceForStructure(structure, newOrigin.toLong())
        }
    }

    private inline fun forEachChunk(box: BoundingBox, action: (Int, Int) -> Unit) {
        for (chunkX in (box.minX() shr 4)..(box.maxX() shr 4)) {
            for (chunkZ in (box.minZ() shr 4)..(box.maxZ() shr 4)) {
                action(chunkX, chunkZ)
            }
        }
    }
}
