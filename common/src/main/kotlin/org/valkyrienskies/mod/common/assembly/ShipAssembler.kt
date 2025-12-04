package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ServerLevelAccessor
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import org.joml.Vector3d
import org.joml.Vector3i
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.mod.api.toBlockPos
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.common.yRange
import org.valkyrienskies.mod.mixin.feature.structure_template.StructureTemplateMixin

object ShipAssembler {
    @OptIn(GameTickOnly::class)
    fun assembleToShip(level: ServerLevel, blocks: Iterable<BlockPos>, scale: Double): ServerShip {
        val eventData = mutableMapOf<String, CompoundTag>()
        VSAssemblyEvents.onCopy.emit(VSAssemblyEvents.OnCopy(level, TODO(), TODO(), eventData))

        val template = StructureTemplate()
        template as StructureTemplateMixin
        template.`vs$fillFromVoxelSet`(level, blocks, TODO(), TODO())



        val ship = level.shipObjectWorld.createNewShipAtBlock(TODO(), false, scale, level.dimensionId)
        ship.isStatic = true
        val centerOfShip = ship.chunkClaim.getCenterBlockCoordinates(level.yRange, Vector3i()).toBlockPos()

        val structureSettings = StructurePlaceSettings().addProcessor(ICopyableProcessor(TODO(), TODO()))
        structureSettings.rotationPivot = centerOfShip


        VSAssemblyEvents.onPasteBeforeBlocksAreLoaded.emit(VSAssemblyEvents.OnPasteBeforeBlocksAreCopied(level, TODO(), Pair(TODO(), ship), TODO(), eventData))
        //TODO what is 2?
        template.placeInWorld(level, centerOfShip, centerOfShip, structureSettings, level.random, 2)
        VSAssemblyEvents.onPasteAfterBlocksAreLoaded.emit(VSAssemblyEvents.OnPasteAfterBlocksAreLoaded(level, TODO(), TODO(), eventData))

        //TODO probably won't work for assembling from ships (splitting)
        val newPos: Vector3d = ship.transform.positionInWorld.add(ship.inertiaData.centerOfMassInShip, Vector3d()).sub(centerOfShip.x.toDouble(), centerOfShip.y.toDouble(), centerOfShip.z.toDouble())

        level.shipObjectWorld.teleportShip(ship, vsCore.newShipTeleportData(newPos))

        ship.isStatic = false

        return ship
    }

    class ICopyableProcessor(
        val oldShipIdToNewShipId: Map<ShipId, ShipId>,
        val centerPositions: Map<Long, Pair<Vector3d, Vector3d>>
    ): StructureProcessor() {
        override fun processBlock(
            levelReader: LevelReader, oldBPos: BlockPos, newBPos: BlockPos,
            oldStructureBlockInfo: StructureTemplate.StructureBlockInfo,
            newStructureBlockInfo: StructureTemplate.StructureBlockInfo, structurePlaceSettings: StructurePlaceSettings
        ): StructureTemplate.StructureBlockInfo? {
            val block = newStructureBlockInfo.state.block
            if (block !is ICopyableBlock) return newStructureBlockInfo
            block.onPaste((levelReader as ServerLevelAccessor).level, newBPos, newStructureBlockInfo.state, oldShipIdToNewShipId, centerPositions, newStructureBlockInfo.nbt)
            return newStructureBlockInfo
        }

        //TODO i don't think it does anything, but i'm not sure
        override fun getType(): StructureProcessorType<*>? = null
    }
}
