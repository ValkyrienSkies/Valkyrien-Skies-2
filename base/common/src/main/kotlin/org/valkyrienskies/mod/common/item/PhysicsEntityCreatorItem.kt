package org.valkyrienskies.mod.common.item

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.apigame.constraints.VSAttachmentConstraint
import org.valkyrienskies.core.apigame.constraints.VSPosDampingConstraint
import org.valkyrienskies.core.apigame.constraints.VSRotDampingAxes
import org.valkyrienskies.core.apigame.constraints.VSRotDampingConstraint
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.entity.VSPhysicsEntity
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toJOMLD
import org.valkyrienskies.mod.common.vsCore

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
            val offsetInLocal: Vector3dc = ctx.clickedFace.normal.toJOMLD().mul(sphereRadius)

            val shipOn = ctx.level.getShipManagingPos(ctx.clickedPos)
            val offsetInGlobal = if (shipOn != null) {
                shipOn.transform.shipToWorldRotation.transform(offsetInLocal, Vector3d())
            } else {
                offsetInLocal
            }

            val entityPos: Vector3dc = if (shipOn != null) {
                 shipOn.transform.shipToWorld.transformPosition(ctx.clickLocation.toJOML()).add(offsetInGlobal)
            } else {
                ctx.clickLocation.toJOML().add(offsetInGlobal)
            }

            val transform = vsCore.newShipTransform(entityPos, Vector3d())
            val physicsEntityData = VSPhysicsEntity.createBasicSphereData(shipId, transform, sphereRadius)
            entity.setPhysicsEntityData(physicsEntityData)
            entity.setPos(entityPos.x(), entityPos.y(), entityPos.z())
            level.addFreshEntity(entity)

            // Example of adding a constraint to a physics entity
            if (shipOn != null) {
                val attachCompliance = 1e-8
                val attachMaxForce = 1e10
                // Attach the click position of the ship to the surface of the physics entity
                val attachConstraint = VSAttachmentConstraint(
                    shipOn.id, physicsEntityData.shipId, attachCompliance, ctx.clickLocation.toJOML(), offsetInGlobal.mul(-1.0, Vector3d()),
                    attachMaxForce, 0.0
                )
                val posDampingConstraint = VSPosDampingConstraint(
                    shipOn.id, physicsEntityData.shipId, attachCompliance, ctx.clickLocation.toJOML(), offsetInGlobal.mul(-1.0, Vector3d()),
                    attachMaxForce, 0.1
                )
                val rotDampingConstraint = VSRotDampingConstraint(
                    shipOn.id, physicsEntityData.shipId, attachCompliance, shipOn.transform.shipToWorldRotation.invert(
                        Quaterniond()
                    ),
                    Quaterniond(), 1e10, 0.1, VSRotDampingAxes.ALL_AXES
                )
                level.shipObjectWorld.createNewConstraint(attachConstraint)
                level.shipObjectWorld.createNewConstraint(posDampingConstraint)
                level.shipObjectWorld.createNewConstraint(rotDampingConstraint)
            }
        }

        return super.useOn(ctx)
    }
}
