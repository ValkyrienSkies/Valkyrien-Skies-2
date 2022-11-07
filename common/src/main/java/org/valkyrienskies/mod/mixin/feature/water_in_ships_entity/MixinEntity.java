package org.valkyrienskies.mod.mixin.feature.water_in_ships_entity;

import javax.annotation.Nullable;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    public abstract void setPos(double x, double y, double z);

    @Shadow
    public abstract Vec3 position();

    @Shadow
    public abstract boolean updateInWaterStateAndDoFluidPushing();

    @Shadow
    public abstract void updateFluidOnEyes();

    @Unique
    private boolean isModifyingWaterState = false;

    @Shadow
    public Level level;

    @Shadow
    public boolean wasTouchingWater;
    @Unique
    public boolean oldWasTouchingWater;
    @Unique
    public boolean shipWasTouchingWater;

    @Shadow
    private AABB bb;

    @Shadow
    @Nullable
    protected Tag<Fluid> fluidOnEyes;

    @Inject(
        at = @At("TAIL"),
        method = "updateInWaterStateAndDoFluidPushing",
        cancellable = true
    )
    private void afterFluidStateUpdate(final CallbackInfoReturnable<Boolean> cir) {
        if (isModifyingWaterState) {
            return;
        }

        oldWasTouchingWater = this.wasTouchingWater;
        this.wasTouchingWater = shipWasTouchingWater;

        isModifyingWaterState = true;

        final Vec3 pos = this.position();

        final double origX = pos.x;
        final double origY = pos.y;
        final double origZ = pos.z;

        VSGameUtilsKt.transformToNearbyShipsAndWorld(this.level, origX, origY, origZ, this.bb.getSize(), (x, y, z) -> {
            this.setPos(x, y, z);
            cir.setReturnValue(this.updateInWaterStateAndDoFluidPushing());
            this.setPos(origX, origY, origZ);

        });

        isModifyingWaterState = false;

        shipWasTouchingWater = this.wasTouchingWater;
        this.wasTouchingWater = oldWasTouchingWater;
    }

    @Inject(
        at = @At("TAIL"),
        method = "updateFluidOnEyes",
        cancellable = true
    )
    private void afterFluidStateUpdateEyes(final CallbackInfo ci) {
        if (isModifyingWaterState || this.fluidOnEyes != null) {
            return;
        }

        isModifyingWaterState = true;

        final Vec3 pos = this.position();

        final double origX = pos.x;
        final double origY = pos.y;
        final double origZ = pos.z;

        VSGameUtilsKt.transformToNearbyShipsAndWorld(this.level, origX, origY, origZ, this.bb.getSize(), (x, y, z) -> {
            this.setPos(x, y, z);
            this.updateFluidOnEyes();
            this.setPos(origX, origY, origZ);

        });

        isModifyingWaterState = false;
    }

}
