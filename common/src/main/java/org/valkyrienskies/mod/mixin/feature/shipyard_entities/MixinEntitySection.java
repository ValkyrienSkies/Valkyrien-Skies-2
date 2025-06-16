package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.world.OfShip;

@Mixin(EntitySection.class)
public class MixinEntitySection implements OfShip {

    @Unique
    private Ship ofShip;

    @Override
    public Ship getShip() {
        return ofShip;
    }

    @Override
    public void setShip(final Ship ship) {
        this.ofShip = ship;
    }

    @ModifyVariable(
        method = "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
        at = @At("HEAD"),
        ordinal = 0,
        argsOnly = true)
    AABB modifyAABB1(final AABB aabb) {
        if (ofShip != null) {
            return VectorConversionsMCKt.toMinecraft(
                VectorConversionsMCKt.toJOML(aabb).transform(ofShip.getWorldToShip()));
        } else {
            return aabb;
        }
    }

    @ModifyVariable(
        method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
        at = @At("HEAD"),
        ordinal = 0,
        argsOnly = true)
    AABB modifyAABB2(final AABB aabb) {
        if (ofShip != null) {
            return VectorConversionsMCKt.toMinecraft(
                VectorConversionsMCKt.toJOML(aabb).transform(ofShip.getWorldToShip()));
        } else {
            return aabb;
        }
    }
}
