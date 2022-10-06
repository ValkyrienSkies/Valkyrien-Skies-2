package org.valkyrienskies.mod.mixin.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    @Shadow
    public abstract boolean onClimbable();

    @Unique
    private boolean isModifyingClimbable = false;

    public MixinLivingEntity(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(
        at = @At("TAIL"),
        method = "onClimbable",
        cancellable = true
    )
    private void onClimbableMixin(final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            if (isModifyingClimbable) {
                return;
            }

            isModifyingClimbable = true;

            final Vec3 pos = this.position();

            final double origX = pos.x;
            final double origY = pos.y;
            final double origZ = pos.z;

            VSGameUtilsKt.transformToNearbyShipsAndWorld(this.level, origX, origY, origZ, 1, (x, y, z) -> {
                this.setPos(x, y, z);
                cir.setReturnValue(onClimbable());
                this.setPos(origX, origY, origZ);

            });

            isModifyingClimbable = false;
        }
    }
}
