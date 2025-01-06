package org.valkyrienskies.mod.common.item

import net.minecraft.Util
import net.minecraft.network.chat.TextComponent
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Rotation.NONE
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3d
import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.impl.bodies.properties.BodyKinematicsFactory
import org.valkyrienskies.core.impl.game.ships.ShipDataCommon
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toBlockPos
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toJOMLD
import org.valkyrienskies.mod.common.yRange
import org.valkyrienskies.mod.util.relocateBlock
import java.util.function.DoubleSupplier

class ShipCreatorItem(
    properties: Properties, private val scale: DoubleSupplier, private val minScaling: DoubleSupplier
) : Item(properties) {

    override fun isFoil(stack: ItemStack): Boolean {
        return true
    }

    @OptIn(VsBeta::class)
    override fun useOn(ctx: UseOnContext): InteractionResult {
        val level = ctx.level as? ServerLevel ?: return super.useOn(ctx)
        val blockPos = ctx.clickedPos
        val blockState: BlockState = level.getBlockState(blockPos)

        if (!level.isClientSide) {
            val parentShip = ctx.level.getShipManagingPos(blockPos)
            if (!blockState.isAir) {
                // Make a ship
                val dimensionId = level.dimensionId

                val scale = scale.asDouble
                val minScaling = minScaling.asDouble

                val serverShip =
                    level.shipObjectWorld.createNewShipAtBlock(blockPos.toJOML(), false, scale, dimensionId)

                val centerPos = serverShip.chunkClaim.getCenterBlockCoordinates(level.yRange).toBlockPos()

                // Move the block from the world to a ship
                level.relocateBlock(blockPos, centerPos, true, serverShip, NONE)

                ctx.player?.sendMessage(TextComponent("SHIPIFIED!"), Util.NIL_UUID)
                if (parentShip != null) {
                    // Compute the ship transform
                    val newShipPosInWorld =
                        parentShip.shipToWorld.transformPosition(blockPos.toJOMLD().add(0.5, 0.5, 0.5))
                    val newShipPosInShipyard = blockPos.toJOMLD().add(0.5, 0.5, 0.5)
                    val newShipRotation = parentShip.transform.shipToWorldRotation
                    var newShipScaling = parentShip.transform.shipToWorldScaling.mul(scale, Vector3d())
                    if (newShipScaling.x() < minScaling) {
                        // Do not allow scaling to go below minScaling
                        newShipScaling = Vector3d(minScaling, minScaling, minScaling)
                    }
                    val newKinematics = BodyKinematicsFactory.create(
                        Vector3d(),
                        Vector3d(),
                        newShipPosInWorld,
                        newShipRotation,
                        newShipScaling,
                        newShipPosInShipyard,
                    )
                    (serverShip as ShipDataCommon).kinematics = newKinematics
                }
            }
        }

        return super.useOn(ctx)
    }
}
