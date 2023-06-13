package org.valkyrienskies.mod.forge.mixin.feature.water_in_ships_entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.extensions.IForgeEntity;
import net.minecraftforge.fluids.FluidType;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Entity.class)
public abstract class MixinEntity {
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
    public abstract boolean touchingUnloadedChunk();

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract boolean isPushedByFluid();

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract void setDeltaMovement(Vec3 vec3);

    @Unique
    private boolean isShipWater = false;

    @Shadow
    protected abstract void setFluidTypeHeight(FluidType type, double height);

    @Inject(
        at = @At("HEAD"),
        method = "updateFluidHeightAndDoFluidPushing()V",
        remap = false
    )
    // Overwrite the forge method, since it's written in a way that's really hard to precisely mixin into.
    private void afterFluidStateUpdate(final CallbackInfo callbackInfo) {
        if (this.touchingUnloadedChunk()) {
            return;
        }
        VSGameUtilsKt.transformFromWorldToNearbyShipsAndWorld(level, this.getBoundingBox().deflate(0.001), aabb -> {
            int i = Mth.floor(aabb.minX);
            int j = Mth.ceil(aabb.maxX);
            int k = Mth.floor(aabb.minY);
            int l = Mth.ceil(aabb.maxY);
            int i1 = Mth.floor(aabb.minZ);
            int j1 = Mth.ceil(aabb.maxZ);
            double d0 = 0.0;
            boolean flag = this.isPushedByFluid();
            boolean flag1 = false;
            Vec3 vec3 = Vec3.ZERO;
            boolean k1 = false;
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
            Object2ObjectArrayMap<FluidType, MutableTriple>
                interimCalcs = new Object2ObjectArrayMap<FluidType, MutableTriple>((Integer) FluidType.SIZE.get() - 1);
            for (int l1 = i; l1 < j; ++l1) {
                for (int i2 = k; i2 < l; ++i2) {
                    for (int j2 = i1; j2 < j1; ++j2) {
                        double d1;
                        blockpos$mutableblockpos.set(l1, i2, j2);
                        FluidState fluidstate = this.level.getFluidState(blockpos$mutableblockpos);
                        FluidType fluidType2 = fluidstate.getFluidType();
                        if (fluidType2.isAir() || !((d1 =
                            (double) ((float) i2 + fluidstate.getHeight(this.level, blockpos$mutableblockpos))) >=
                            aabb.minY)) continue;
                        flag1 = true;
                        MutableTriple interim2 =
                            interimCalcs.computeIfAbsent(fluidType2, t -> MutableTriple.of(0.0, Vec3.ZERO, 0));
                        interim2.setLeft(Math.max(d1 - aabb.minY, (Double) interim2.getLeft()));
                        if (!((IForgeEntity) this).isPushedByFluid(fluidType2)) continue;
                        Vec3 vec31 = fluidstate.getFlow(this.level, blockpos$mutableblockpos);
                        if ((Double) interim2.getLeft() < 0.4) {
                            vec31 = vec31.scale((Double) interim2.getLeft());
                        }
                        interim2.setMiddle(((Vec3) interim2.getMiddle()).add(vec31));
                        interim2.setRight((Integer) interim2.getRight() + 1);
                    }
                }
            }
            interimCalcs.forEach((fluidType, interim) -> {
                if (((Vec3) interim.getMiddle()).length() > 0.0) {
                    if ((Integer) interim.getRight() > 0) {
                        interim.setMiddle(((Vec3) interim.getMiddle()).scale(
                            1.0 / (double) ((Integer) interim.getRight()).intValue()));
                    }
                    if (!Player.class.isInstance(this)) {
                        interim.setMiddle(((Vec3) interim.getMiddle()).normalize());
                    }
                    Vec3 vec32 = this.getDeltaMovement();
                    interim.setMiddle(((Vec3) interim.getMiddle()).scale(
                        ((IForgeEntity) this).getFluidMotionScale((FluidType) fluidType)));
                    double d2 = 0.003;
                    if (Math.abs(vec32.x) < 0.003 && Math.abs(vec32.z) < 0.003 &&
                        ((Vec3) interim.getMiddle()).length() < 0.0045000000000000005) {
                        interim.setMiddle(((Vec3) interim.getMiddle()).normalize().scale(0.0045000000000000005));
                    }
                    this.setDeltaMovement(this.getDeltaMovement().add((Vec3) interim.getMiddle()));
                }
                this.setFluidTypeHeight((FluidType) fluidType, (Double) interim.getLeft());
            });
        });
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "updateFluidOnEyes"
    )
    private FluidState getFluidStateRedirect(final Level level, final BlockPos blockPos,
        final Operation<FluidState> getFluidState) {
        final FluidState[] fluidState = {getFluidState.call(level, blockPos)};
        isShipWater = false;
        if (fluidState[0].isEmpty()) {

            final double d = this.getEyeY() - 0.1111111119389534;

            final double origX = this.getX();
            final double origY = d;
            final double origZ = this.getZ();

            VSGameUtilsKt.transformToNearbyShipsAndWorld(this.level, origX, origY, origZ, this.bb.getSize(),
                (x, y, z) -> {
                    fluidState[0] = getFluidState.call(level, BlockPos.containing(x, y, z));
                });
            isShipWater = true;
        }
        return fluidState[0];
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"),
        method = "updateFluidOnEyes"
    )
    private float fluidHeightOverride(final FluidState instance, final BlockGetter arg, final BlockPos arg2,
        final Operation<Float> getHeight) {
        if (!instance.isEmpty() && this.level instanceof Level) {

            if (isShipWater) {
                if (instance.isSource()) {
                    return 1;
                }
            }

        }
        return getHeight.call(instance, arg, arg2);
    }

}
