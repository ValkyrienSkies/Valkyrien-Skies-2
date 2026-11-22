package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.create.ChainConveyorFrames;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.ChainConveyorCrossFrameDuck;

// Rendering between frames (see ChainConveyorFrames).
@Mixin(ChainConveyorRenderer.class)
public abstract class MixinChainConveyorRendererFrames {
    // Ticks update the stats 20 times a second; refresh per frame so the chain moves with the ship smoothly.
    @Inject(method = "renderChains", at = @At("HEAD"))
    private void vs$refresh(final ChainConveyorBlockEntity be, final PoseStack ms, final MultiBufferSource buffer,
        final int light, final int overlay, final CallbackInfo ci) {
        ((ChainConveyorCrossFrameDuck) be).vs$refreshCrossFrame();
    }

    // The far (static, flat) chain is picked by distance to the chain's midpoint, which Create takes as the block
    // position plus half the raw offset. Use the chain's actual midpoint, in the world.
    @WrapOperation(
        method = "renderChains",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z")
    )
    private boolean vs$actualMidpoint(final Vec3 camera, final Position midpoint, final double distance,
        final Operation<Boolean> original, @Local(argsOnly = true) final ChainConveyorBlockEntity be,
        @Local final ConnectionStats stats) {
        final Vec3 mid = stats.start().add(stats.end()).scale(0.5);
        return original.call(camera, ChainConveyorFrames.toWorld(be.getLevel(), be.getBlockPos(), mid), distance);
    }
}
