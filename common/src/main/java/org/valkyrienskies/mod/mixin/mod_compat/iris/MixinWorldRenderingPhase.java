package org.valkyrienskies.mod.mixin.mod_compat.iris;

import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.minecraft.client.renderer.RenderType;

import java.util.Arrays;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.fluid.client.ShipFluidRenderTypes;


@Mixin(WorldRenderingPhase.class)
public abstract class MixinWorldRenderingPhase {
    @Shadow @Final @Mutable
    private static WorldRenderingPhase[] $VALUES;

    @Invoker("<init>")
    public static WorldRenderingPhase invokeInit(String name, int ordinal) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void addPhase(CallbackInfo ci) {
        WorldRenderingPhase custom =
            invokeInit("AIR_CULL", $VALUES.length);

        WorldRenderingPhase[] newValues =
            Arrays.copyOf($VALUES, $VALUES.length + 1);

        newValues[newValues.length - 1] = custom;
        $VALUES = newValues;
    }

    @Inject(
        method = "fromTerrainRenderType",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void onFromTerrainRenderType(RenderType renderType, CallbackInfoReturnable<WorldRenderingPhase> ci) {
        if (renderType == ShipFluidRenderTypes.AIR_CULL_RENDER_TYPE) {
            ci.setReturnValue(invokeInit("AIR_CULL", $VALUES.length));
        }
    }
}