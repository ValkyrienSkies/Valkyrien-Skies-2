package org.valkyrienskies.mod.fabric.common

/*
import com.simibubi.create.content.contraptions.components.structureMovement.AbstractContraptionEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import org.valkyrienskies.core.api.ships.ContraptionWingProvider
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.WingManager
import org.valkyrienskies.mod.common.block.WingBlock
import org.valkyrienskies.mod.common.entity.handling.AbstractShipyardEntityHandler

object ContraptionShipyardEntityHandlerFabric: AbstractShipyardEntityHandler() {
    override fun freshEntityInShipyard(entity: Entity, ship: Ship) {
        if (entity is AbstractContraptionEntity && ship is LoadedServerShip) {
            entity as ContraptionWingProvider
            val attachment = ship.getAttachment(WingManager::class.java)!!
            entity.wingGroupId = attachment.createWingGroup()
            entity.contraption.blocks.forEach { (pos: BlockPos, blockInfo: StructureTemplate.StructureBlockInfo) ->
                val block = blockInfo.state.block
                if (block is WingBlock) {
                    val wing = block.getWing(null, null, blockInfo.state)
                    attachment.setWing(entity.wingGroupId, pos.x, pos.y, pos.z, wing)
                }
            }
            val transform = entity.computeContraptionWingTransform()
            attachment.setWingGroupTransform(entity.wingGroupId, transform)
        }
    }

    override fun entityRemovedFromShipyard(entity: Entity, ship: Ship) {
        if (entity is AbstractContraptionEntity && ship is LoadedServerShip) {
            entity as ContraptionWingProvider
            val attachment = ship.getAttachment(WingManager::class.java)!!
            attachment.deleteWingGroup(entity.wingGroupId)
            entity.wingGroupId = -1
        }
    }
}
 */
