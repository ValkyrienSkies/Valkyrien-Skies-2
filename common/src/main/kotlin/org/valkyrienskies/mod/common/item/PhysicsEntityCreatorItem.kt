package org.valkyrienskies.mod.common.item

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import org.joml.Vector3d
import org.valkyrienskies.core.impl.game.ships.ShipTransformImpl.Companion
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.entity.VSPhysicsEntity
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toJOMLD

class PhysicsEntityCreatorItem(
    properties: Properties
) : Item(properties) {
    override fun isFoil(stack: ItemStack): Boolean {
        return true
    }

    override fun useOn(ctx: UseOnContext): InteractionResult {
        val level = ctx.level as? ServerLevel ?: return super.useOn(ctx)

        if (!level.isClientSide) {
            val entity = ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE.create(level)!!
            val shipId = level.shipObjectWorld.allocateShipId(level.dimensionId)
            val sphereRadius = 0.5
            val offset = ctx.clickedFace.normal.toJOMLD().mul(sphereRadius)
            val entityPos = ctx.clickLocation.toJOML().add(offset)
            val transform = Companion.create(entityPos, Vector3d())
            val physicsEntityData = VSPhysicsEntity.createBasicSphereData(shipId, transform, sphereRadius)
            entity.setPhysicsEntityData(physicsEntityData)
            entity.setPos(entityPos.x, entityPos.y, entityPos.z)
            level.addFreshEntity(entity)
        }

        return super.useOn(ctx)
    }
}
