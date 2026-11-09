package org.valkyrienskies.mod.mixin.feature.fluid_camera_fix;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.fluid.CameraFluidState;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow
    public abstract Vec3 getPosition();

    @Unique
    private boolean vs$submergedInShip;
    @Unique
    private CameraFluidState.Sample vs$centerSample;
    @Unique
    private ClientLevel vs$sampleLevel;
    @Unique
    private final CameraFluidState.Grace vs$grace = new CameraFluidState.Grace();

    @Inject(method = "reset", at = @At("TAIL"))
    private void vs$resetFluid(final CallbackInfo ci) {
        vs$grace.clear();
        vs$centerSample = null;
        vs$sampleLevel = null;
        vs$submergedInShip = false;
    }

    @WrapMethod(method = "getFluidInCamera")
    private FogType vs$cameraGrace(final Operation<FogType> original) {
        vs$centerSample = null;
        vs$sampleLevel = null;
        vs$submergedInShip = false;
        try {
            final FogType result = original.call();
            if (vs$centerSample == null) {
                vs$grace.clear();
                return result;
            }
            return vs$grace.suppress(vs$sampleLevel, vs$centerSample.dryDomain(),
                VSGameConfig.CLIENT.getSealedAreaCameraGracePeriod()) ? FogType.NONE : result;
        } finally {
            // Answers live for one query only: snapshots and render poses can change within a tick.
            vs$centerSample = null;
            vs$sampleLevel = null;
            vs$submergedInShip = false;
        }
    }

    @WrapOperation(method = "getFluidInCamera", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
    private FluidState vs$centerFluid(final BlockGetter level, final BlockPos block,
        final Operation<FluidState> original) {
        final FluidState vanilla = original.call(level, block);
        if (!(level instanceof final ClientLevel clientLevel)) return vanilla;
        vs$sampleLevel = clientLevel;
        vs$centerSample = CameraFluidState.sample(clientLevel, getPosition(), vanilla,
            VSGameConfig.CLIENT.getRenderFloodedFluidFog());
        vs$submergedInShip = vs$centerSample.submergedInShip();
        return vs$centerSample.fluid();
    }

    @WrapOperation(method = "getFluidInCamera", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
    private FluidState vs$nearPlaneFluid(final BlockGetter level, final BlockPos block,
        final Operation<FluidState> original, @Local(ordinal = 1) final Vec3 worldSample) {
        final FluidState vanilla = original.call(level, block);
        vs$submergedInShip = false;
        if (!(level instanceof final ClientLevel clientLevel)) return vanilla;
        // Vanilla's other Vec3 local is the offset. This one is camera position + offset.
        final CameraFluidState.Sample sample = CameraFluidState.sample(clientLevel, worldSample, vanilla,
            VSGameConfig.CLIENT.getRenderFloodedFluidFog());
        vs$submergedInShip = sample.submergedInShip();
        return sample.fluid();
    }

    @WrapOperation(method = "getFluidInCamera", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float vs$fluidHeight(final FluidState fluid, final BlockGetter level, final BlockPos block,
        final Operation<Float> original) {
        // Local immersion was already tested. A world-axis height cannot describe a rotated surface.
        return vs$submergedInShip ? Float.POSITIVE_INFINITY : original.call(fluid, level, block);
    }
}
