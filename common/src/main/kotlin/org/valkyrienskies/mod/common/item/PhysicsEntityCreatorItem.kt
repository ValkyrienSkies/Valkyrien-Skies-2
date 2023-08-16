package org.valkyrienskies.mod.common.item

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld

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
            entity.setShipId(shipId)
            entity.setPos(ctx.clickLocation)
            level.addFreshEntity(entity)
        }

        return super.useOn(ctx)
    }
}
