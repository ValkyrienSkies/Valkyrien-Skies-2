package org.valkyrienskies.mod.mixin.mod_compat.theatrical;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.imabad.theatrical.blockentities.light.BaseLightBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * This class makes sure the beams render in world space to prevent them being culled/invisible.
 * @see MixinMovingLightRenderer
 */
@Mixin(targets = {"dev.imabad.theatrical.client.blockentities.FixtureRenderer$1"})
public class MixinFixtureRenderer<T extends BaseLightBlockEntity> {

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 redirectAtLowerCornerOf(Vec3i pos) {
        return VSGameUtilsKt.toWorldRenderCoordinates(Minecraft.getInstance().level, Vec3.atLowerCornerOf(pos));
    }

    @WrapMethod(
        method = "getPos",
        remap = false
    )
    private Vec3 wrapGetPos(float partialTick, Operation<Vec3> original) {
        return VSGameUtilsKt.toWorldRenderCoordinates(Minecraft.getInstance().level, original.call(partialTick));
    }
}
