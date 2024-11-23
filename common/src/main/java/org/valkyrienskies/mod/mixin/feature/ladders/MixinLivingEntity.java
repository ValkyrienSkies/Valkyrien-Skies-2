package org.valkyrienskies.mod.mixin.feature.ladders;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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
import org.valkyrienskies.mod.mixin.accessors.entity.EntityAccessor;

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

            final EntityAccessor thisAsAccessor = (EntityAccessor) this;
            final BlockPos originalBlockPosition = thisAsAccessor.getBlockPosition();

            VSGameUtilsKt.transformToNearbyShipsAndWorld(this.level, origX, origY, origZ, 1, (x, y, z) -> {

                // Only run this if we haven't modified cir yet
                if (cir.getReturnValue() != Boolean.TRUE) {
                    // Modify the block position, then check if we can climb ladders
                    thisAsAccessor.setBlockPosition(BlockPos.containing(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
                    thisAsAccessor.setFeetBlockState(null);
                    if (onClimbable()) {
                        cir.setReturnValue(true);
                    }
                }

            });
            // Restore the original block position
            thisAsAccessor.setBlockPosition(originalBlockPosition);
            thisAsAccessor.setFeetBlockState(null);
            isModifyingClimbable = false;
        }
    }
}
