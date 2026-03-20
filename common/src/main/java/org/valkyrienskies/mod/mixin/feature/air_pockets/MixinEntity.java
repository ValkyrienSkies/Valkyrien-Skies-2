package org.valkyrienskies.mod.mixin.feature.air_pockets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;
import org.valkyrienskies.mod.mixinducks.feature.air_pockets.ship_water_pockets.ShipWaterPocketEntityDuck;

@Mixin(Entity.class)
public abstract class MixinEntity implements ShipWaterPocketEntityDuck {
    @Shadow
    public Level level;

    @Shadow
    public abstract double getEyeY();

    @Shadow
    public abstract double getX();

    @Shadow
    public abstract double getZ();

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    protected boolean wasTouchingWater;

    @Unique
    private static final FluidState vs$WATER = Fluids.WATER.defaultFluidState();

    @Unique
    private static final FluidState vs$EMPTY = Fluids.EMPTY.defaultFluidState();

    @Unique
    private long vs$airPocketCacheTick = Long.MIN_VALUE;

    @Unique
    private boolean vs$airPocketCacheValue = false;

    @Override
    public boolean vs$isInShipAirPocketForWorldWater() {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return false;
        final Level lvl = this.level;
        if (lvl == null) return false;

        final long now = lvl.getGameTime();
        if (vs$airPocketCacheTick == now) {
            return vs$airPocketCacheValue;
        }

        final boolean value = vs$computeInShipAirPocketNow(lvl);

        vs$airPocketCacheTick = now;
        vs$airPocketCacheValue = value;
        return value;
    }

    @Unique
    private boolean vs$computeInShipAirPocketNow(final Level level) {
        final AABB bb = this.getBoundingBox();
        final double halfX = (bb.maxX - bb.minX) * 0.5;
        final double halfZ = (bb.maxZ - bb.minZ) * 0.5;
        final double ox = Math.min(0.125, halfX * 0.5);
        final double oz = Math.min(0.125, halfZ * 0.5);

        final double eyeY = this.getEyeY() - 0.1111111119389534;
        final double feetY = bb.minY + 0.1;

        // If we're in any ship fluid (flooded pocket water, shipyard water blocks, etc) we should behave like we're
        // in water, even if the space above is an air pocket.
        if (vs$isInShipFluidAtHeight(level, eyeY, ox, oz) || vs$isInShipFluidAtHeight(level, feetY, ox, oz)) {
            return false;
        }

        return vs$isInAirPocketAtHeight(level, eyeY, ox, oz) || vs$isInAirPocketAtHeight(level, feetY, ox, oz);
    }

    @Unique
    private boolean vs$isInAirPocketAtHeight(final Level level, final double y, final double ox, final double oz) {
        final double x = this.getX();
        final double z = this.getZ();

        if (vs$isAirPocketAtPoint(level, x, y, z)) return true;
        if (ox <= 0.0 && oz <= 0.0) return false;

        if (ox > 0.0) {
            if (vs$isAirPocketAtPoint(level, x + ox, y, z)) return true;
            if (vs$isAirPocketAtPoint(level, x - ox, y, z)) return true;
        }
        if (oz > 0.0) {
            if (vs$isAirPocketAtPoint(level, x, y, z + oz)) return true;
            if (vs$isAirPocketAtPoint(level, x, y, z - oz)) return true;
        }
        if (ox > 0.0 && oz > 0.0) {
            if (vs$isAirPocketAtPoint(level, x + ox, y, z + oz)) return true;
            if (vs$isAirPocketAtPoint(level, x + ox, y, z - oz)) return true;
            if (vs$isAirPocketAtPoint(level, x - ox, y, z + oz)) return true;
            if (vs$isAirPocketAtPoint(level, x - ox, y, z - oz)) return true;
        }

        return false;
    }

    @Unique
    private static boolean vs$isAirPocketAtPoint(final Level level, final double x, final double y, final double z) {
        return ShipWaterPocketManager.isWorldPosInShipWorldFluidSuppressionZone(level, x, y, z);
    }

    @Unique
    private boolean vs$isInShipFluidAtHeight(final Level level, final double y, final double ox, final double oz) {
        final double x = this.getX();
        final double z = this.getZ();

        if (vs$isShipFluidAtPoint(level, x, y, z)) return true;
        if (ox <= 0.0 && oz <= 0.0) return false;

        if (ox > 0.0) {
            if (vs$isShipFluidAtPoint(level, x + ox, y, z)) return true;
            if (vs$isShipFluidAtPoint(level, x - ox, y, z)) return true;
        }
        if (oz > 0.0) {
            if (vs$isShipFluidAtPoint(level, x, y, z + oz)) return true;
            if (vs$isShipFluidAtPoint(level, x, y, z - oz)) return true;
        }
        if (ox > 0.0 && oz > 0.0) {
            if (vs$isShipFluidAtPoint(level, x + ox, y, z + oz)) return true;
            if (vs$isShipFluidAtPoint(level, x + ox, y, z - oz)) return true;
            if (vs$isShipFluidAtPoint(level, x - ox, y, z + oz)) return true;
            if (vs$isShipFluidAtPoint(level, x - ox, y, z - oz)) return true;
        }

        return false;
    }

    @Unique
    private static boolean vs$isShipFluidAtPoint(final Level level, final double x, final double y, final double z) {
        final FluidState overridden = ShipWaterPocketManager.overrideWaterFluidState(level, x, y, z, vs$EMPTY);
        return !overridden.isEmpty();
    }

    @WrapOperation(
        method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;setRemainingFireTicks(I)V",
            ordinal = 1
        ),
        require = 0
    )
    private void preventWaterExtinguishInShipAirPockets(final Entity entity, final int fireTicks,
        final Operation<Void> setRemainingFireTicks) {
        if (vs$isInShipAirPocketForWorldWater()) {
            // Vanilla extinguishes entities in water/powder snow here. Inside ship air pockets, "water" is only world
            // water that should be treated as air, so don't reset fire ticks.
            return;
        }
        setRemainingFireTicks.call(entity, fireTicks);
    }

    @WrapOperation(
        method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getFlow(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"
        ),
        require = 0
    )
    private Vec3 rotateFlowVectorVanilla(final FluidState fluidState, final BlockGetter blockGetter,
        final BlockPos worldBlockPos, final Operation<Vec3> getFlow) {
        final Vec3 originalFlow = getFlow.call(fluidState, blockGetter, worldBlockPos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return originalFlow;
        if (!(blockGetter instanceof final Level level)) return originalFlow;

        final Vec3 rotated = ShipWaterPocketManager.computeRotatedShipFluidFlow(level, worldBlockPos);
        return rotated != null ? rotated : originalFlow;
    }

    @WrapOperation(
        method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
        ),
        require = 0
    )
    private float overrideFluidHeightVanilla(final FluidState fluidState, final BlockGetter blockGetter,
        final BlockPos worldBlockPos, final Operation<Float> getHeight) {
        final float originalHeight = getHeight.call(fluidState, blockGetter, worldBlockPos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return originalHeight;
        if (!(blockGetter instanceof final Level level)) return originalHeight;

        final Float shipHeight = ShipWaterPocketManager.computeShipFluidHeight(level, worldBlockPos);
        return shipHeight != null ? shipHeight.floatValue() : originalHeight;
    }

    @WrapOperation(
        method = "updateFluidHeightAndDoFluidPushing(Ljava/util/function/Predicate;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getFlow(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"
        ),
        require = 0
    )
    private Vec3 rotateFlowVectorForge(final FluidState fluidState, final BlockGetter blockGetter,
        final BlockPos worldBlockPos, final Operation<Vec3> getFlow) {
        final Vec3 originalFlow = getFlow.call(fluidState, blockGetter, worldBlockPos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return originalFlow;
        if (!(blockGetter instanceof final Level level)) return originalFlow;

        final Vec3 rotated = ShipWaterPocketManager.computeRotatedShipFluidFlow(level, worldBlockPos);
        return rotated != null ? rotated : originalFlow;
    }

    @WrapOperation(
        method = "updateFluidHeightAndDoFluidPushing(Ljava/util/function/Predicate;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
        ),
        require = 0
    )
    private float overrideFluidHeightForge(final FluidState fluidState, final BlockGetter blockGetter,
        final BlockPos worldBlockPos, final Operation<Float> getHeight) {
        final float originalHeight = getHeight.call(fluidState, blockGetter, worldBlockPos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return originalHeight;
        if (!(blockGetter instanceof final Level level)) return originalHeight;

        final Float shipHeight = ShipWaterPocketManager.computeShipFluidHeight(level, worldBlockPos);
        return shipHeight != null ? shipHeight.floatValue() : originalHeight;
    }

    @WrapOperation(
        method = "updateFluidOnEyes",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        )
    )
    private FluidState overrideUpdateFluidOnEyes(final Level level, final BlockPos blockPos,
        final Operation<FluidState> getFluidState) {
        final FluidState original = getFluidState.call(level, blockPos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return original;

        final double eyeY = this.getEyeY() - 0.1111111119389534;
        return ShipWaterPocketManager.overrideWaterFluidState(level, this.getX(), eyeY, this.getZ(), original);
    }

    @WrapOperation(
        method = "updateFluidOnEyes",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
        ),
        require = 0
    )
    private float overrideUpdateFluidOnEyesFluidHeight(final FluidState fluidState, final BlockGetter blockGetter,
        final BlockPos worldBlockPos, final Operation<Float> getHeight) {
        final float originalHeight = getHeight.call(fluidState, blockGetter, worldBlockPos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return originalHeight;
        if (!(blockGetter instanceof final Level level)) return originalHeight;

        final Float shipHeight = ShipWaterPocketManager.computeShipFluidHeight(level, worldBlockPos);
        return shipHeight != null ? shipHeight.floatValue() : originalHeight;
    }

    @WrapOperation(
        method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        ),
        require = 0
    )
    private FluidState overrideUpdateFluidHeightAndDoFluidPushing(final Level level, final BlockPos blockPos,
        final Operation<FluidState> getFluidState) {
        final FluidState original = getFluidState.call(level, blockPos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return original;
        return ShipWaterPocketManager.overrideWaterFluidState(level, blockPos, original);
    }

    @WrapOperation(
        method = "updateFluidHeightAndDoFluidPushing(Ljava/util/function/Predicate;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        ),
        require = 0
    )
    private FluidState overrideUpdateFluidHeightAndDoFluidPushingPredicate(final Level level, final BlockPos blockPos,
        final Operation<FluidState> getFluidState) {
        final FluidState original = getFluidState.call(level, blockPos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return original;
        return ShipWaterPocketManager.overrideWaterFluidState(level, blockPos, original);
    }

    @WrapOperation(
        method = "updateSwimming",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        ),
        require = 0
    )
    private FluidState overrideUpdateSwimmingFluidCheck(final Level level, final BlockPos blockPos,
        final Operation<FluidState> getFluidState) {
        final FluidState original = getFluidState.call(level, blockPos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return original;
        return ShipWaterPocketManager.overrideWaterFluidState(level, blockPos, original);
    }

    @Inject(method = "updateInWaterStateAndDoWaterCurrentPushing", at = @At("HEAD"), cancellable = true, require = 0)
    private void cancelWaterCurrentPushingInShipAirPockets(final CallbackInfo ci) {
        if (vs$isInShipAirPocketForWorldWater()) {
            this.wasTouchingWater = false;
            ci.cancel();
        }
    }

    @Inject(method = "clearFire", at = @At("HEAD"), cancellable = true)
    private void vs$preventFireExtinguishInShipAirPockets(final CallbackInfo ci) {
        if (vs$isInShipAirPocketForWorldWater()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "isInFluidType(Ljava/util/function/BiPredicate;Z)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void ignoreFluidTypeChecksInShipAirPockets(
        BiPredicate<?, ?> predicate,
        boolean source,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (vs$isInShipAirPocketForWorldWater()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"), cancellable = true)
    private void vs$cancelSplashInShipAirPockets(final CallbackInfo ci) {
        if (vs$isInShipAirPocketForWorldWater()) {
            ci.cancel();
        }
    }

    @Inject(method = "waterSwimSound", at = @At("HEAD"), cancellable = true)
    private void vs$cancelSwimSplashSoundInShipAirPockets(final CallbackInfo ci) {
        if (vs$isInShipAirPocketForWorldWater()) {
            ci.cancel();
        }
    }

    @Inject(method = "playSwimSound", at = @At("HEAD"), cancellable = true)
    private void vs$cancelSwimSoundInShipAirPockets(final float volume, final CallbackInfo ci) {
        if (vs$isInShipAirPocketForWorldWater()) {
            ci.cancel();
        }
    }

    @Inject(method = "isInWater", at = @At("RETURN"), cancellable = true)
    private void vs$overrideInWaterFlagInShipAirPockets(final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (vs$isInShipAirPocketForWorldWater()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isInWaterOrRain", at = @At("RETURN"), cancellable = true)
    private void vs$overrideInWaterOrRainFlagInShipAirPockets(final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (vs$isInShipAirPocketForWorldWater()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isInWaterRainOrBubble", at = @At("RETURN"), cancellable = true)
    private void vs$overrideInWaterRainOrBubbleFlagInShipAirPockets(final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (vs$isInShipAirPocketForWorldWater()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isInWaterOrBubble", at = @At("RETURN"), cancellable = true)
    private void vs$overrideInWaterOrBubbleFlagInShipAirPockets(final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (vs$isInShipAirPocketForWorldWater()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isUnderWater", at = @At("RETURN"), cancellable = true)
    private void vs$overrideUnderWaterFlagInShipAirPockets(final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (vs$isInShipAirPocketForWorldWater()) {
            cir.setReturnValue(false);
        }
    }
}
