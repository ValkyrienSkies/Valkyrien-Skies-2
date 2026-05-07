package org.valkyrienskies.mod.forge.mixin.feature.water_in_ships_entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import net.neoforged.neoforge.fluids.FluidType;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.joml.primitives.AABBd;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Debug(export = true)
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

    @Shadow
    public abstract void updateFluidHeightAndDoFluidPushing();

    @Unique
    private boolean VS$finishComputingFluidPushing = false;
    @Unique
    private Object2ObjectMap VS$interimCalcs = null;
    @Unique
    private Ship VS$ship = null;

    @Unique //avoid realloc
    private AABBd VS$tmpAABB = new AABBd();

    @WrapOperation(method = "updateFluidHeightAndDoFluidPushing()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"),
        remap = false)
    private AABB returnShipedAABB(Entity instance, Operation<AABB> original) {
        if (VS$ship == null)
            return original.call(instance);
        return VectorConversionsMCKt.toMinecraft(VectorConversionsMCKt.set(VS$tmpAABB, original.call(instance)).transform(VS$ship.getWorldToShip()));
    }

    @Inject(method = "updateFluidHeightAndDoFluidPushing()V",
        at = @At(
            value = "JUMP", opcode = Opcodes.IFNULL, //inject at the "if (interimCalcs != null)" after the loop end
            shift = Shift.BY, by = -1 //shift before interimCalcs is loaded on the stack
        ),
        cancellable = true,
        remap = false, locals = LocalCapture.CAPTURE_FAILHARD, require = 1)
    private void recallUpdateFluidWithVSShip(CallbackInfo ci, AABB aabb, @Local LocalRef<Object2ObjectMap> interimCalcs) {
        if (VS$finishComputingFluidPushing)
            return;
        if (VS$ship != null) {
            if (interimCalcs.get() != null) {
                if (VS$interimCalcs == null)
                    VS$interimCalcs = interimCalcs.get();
                else
                    VS$interimCalcs.putAll(interimCalcs.get());
            }
            ci.cancel();
            return;
        }

        VS$interimCalcs = interimCalcs.get();

        for (Ship ship : VSGameUtilsKt.getShipsIntersecting(level, this.getBoundingBox())) {
            VS$ship = ship;
            updateFluidHeightAndDoFluidPushing();
        }

        interimCalcs.set(VS$interimCalcs);
        VS$interimCalcs = null;
        VS$ship = null;
        VS$finishComputingFluidPushing = true;
    }

    @Inject(method = "updateFluidHeightAndDoFluidPushing()V", at = @At("TAIL"), remap = false)
    private void resetState(CallbackInfo ci) {
        VS$interimCalcs = null;
        VS$finishComputingFluidPushing = false;
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
