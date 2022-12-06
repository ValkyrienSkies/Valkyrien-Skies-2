package org.valkyrienskies.mod.mixin.feature.water_in_ships_entity;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    public abstract void setPos(double x, double y, double z);

    @Shadow
    public abstract Vec3 position();

    @Unique
    private boolean isModifyingWaterState = false;

    @Shadow
    public Level level;
    @Shadow
    private AABB bb;

    @Shadow
    public abstract double getEyeY();

    @Shadow
    public abstract double getX();

    @Shadow
    public abstract double getZ();

    @Shadow
    public abstract boolean updateFluidHeightAndDoFluidPushing(TagKey<Fluid> tagKey, double d);

    @Shadow
    public abstract boolean touchingUnloadedChunk();

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract boolean isPushedByFluid();

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract void setDeltaMovement(Vec3 vec3);

    @Shadow
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;
    @Unique
    private boolean isShipWater = false;

    @Inject(
        at = @At("HEAD"),
        method = "updateFluidHeightAndDoFluidPushing",
        cancellable = true
    )
    // Overwrite the vanilla method, since it's written in a way that's really hard to precisely mixin into.
    private void afterFluidStateUpdate(final TagKey<Fluid> tagKey, final double d,
        final CallbackInfoReturnable<Boolean> cir) {

        if (this.touchingUnloadedChunk()) {
            cir.setReturnValue(false);
            return;
        }

        final Vec3[] vec3ref = {Vec3.ZERO};
        final int[] oref = {0};
        final boolean[] bl2ref = {false};
        final double[] e = {0.0};

        // The only change, gather fluid forces from nearby ships as well as the world.
        VSGameUtilsKt.transformFromWorldToNearbyShipsAndWorld(level, this.getBoundingBox().deflate(0.001), aABB -> {
            final int i = Mth.floor(aABB.minX);
            final int j = Mth.ceil(aABB.maxX);
            final int k = Mth.floor(aABB.minY);
            final int l = Mth.ceil(aABB.maxY);
            final int m = Mth.floor(aABB.minZ);
            final int n = Mth.ceil(aABB.maxZ);
            final boolean bl = this.isPushedByFluid();
            final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            for (int p = i; p < j; ++p) {
                for (int q = k; q < l; ++q) {
                    for (int r = m; r < n; ++r) {
                        final double f;
                        mutableBlockPos.set(p, q, r);
                        final FluidState fluidState = this.level.getFluidState(mutableBlockPos);
                        if (!fluidState.is(tagKey) ||
                            !((f = (float) q + fluidState.getHeight(this.level, mutableBlockPos)) >=
                                aABB.minY)) {
                            continue;
                        }
                        bl2ref[0] = true;
                        e[0] = Math.max(f - aABB.minY, e[0]);
                        if (!bl) {
                            continue;
                        }
                        Vec3 vec32 = fluidState.getFlow(this.level, mutableBlockPos);
                        if (e[0] < 0.4) {
                            vec32 = vec32.scale(e[0]);
                        }
                        vec3ref[0] = vec3ref[0].add(vec32);
                        ++oref[0];
                    }
                }
            }
        });

        Vec3 vec3 = vec3ref[0];
        final boolean bl2 = bl2ref[0];
        final int o = oref[0];

        if (vec3.length() > 0.0) {
            if (o > 0) {
                vec3 = vec3.scale(1.0 / (double) o);
            }
            if (!Player.class.isInstance(this)) {
                vec3 = vec3.normalize();
            }
            final Vec3 vec33 = this.getDeltaMovement();
            vec3 = vec3.scale(d);
            final double g = 0.003;
            if (Math.abs(vec33.x) < 0.003 && Math.abs(vec33.z) < 0.003 && vec3.length() < 0.0045000000000000005) {
                vec3 = vec3.normalize().scale(0.0045000000000000005);
            }
            this.setDeltaMovement(this.getDeltaMovement().add(vec3));
        }
        this.fluidHeight.put(tagKey, e[0]);

        cir.setReturnValue(bl2);
    }

    @Redirect(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "updateFluidOnEyes"
    )
    private FluidState getFluidStateRedirect(final Level level, final BlockPos blockPos) {
        final FluidState[] fluidState = {level.getFluidState(blockPos)};
        isShipWater = false;
        if (fluidState[0].isEmpty()) {

            final double d = this.getEyeY() - 0.1111111119389534;

            final double origX = this.getX();
            final double origY = d;
            final double origZ = this.getZ();

            VSGameUtilsKt.transformToNearbyShipsAndWorld(this.level, origX, origY, origZ, this.bb.getSize(),
                (x, y, z) -> {
                    fluidState[0] = level.getFluidState(new BlockPos(x, y, z));
                });
            isShipWater = true;
        }
        return fluidState[0];
    }

    @Redirect(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"),
        method = "updateFluidOnEyes"
    )
    private float fluidHeightOverride(final FluidState instance, final BlockGetter arg, final BlockPos arg2) {
        if (!instance.isEmpty() && this.level instanceof Level) {

            if (isShipWater) {
                if (instance.isSource()) {
                    return 1;
                }
            }

        }
        return instance.getHeight(arg, arg2);
    }

}

record FluidForceData(int o, boolean bl2, Vec3 vec3) {
}
