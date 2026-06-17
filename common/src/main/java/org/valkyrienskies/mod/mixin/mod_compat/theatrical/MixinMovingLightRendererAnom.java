package org.valkyrienskies.mod.mixin.mod_compat.theatrical;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Anom = anonymous class
 * This mixin targets an anonymous inner class of MovingLightRenderer.
 * This class makes sure the beams render in world space to prevent them being culled/invisible.
 *
 * @see MixinMovingLightRenderer
 */
@Mixin(targets = {"dev.imabad.theatrical.client.blockentities.MovingLightRenderer$1"})
public class MixinMovingLightRendererAnom {
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 redirectAtLowerCornerOf(Vec3i pos) {
        Level level = Minecraft.getInstance().level;

        Vec3 vanilla = Vec3.atLowerCornerOf(pos);

        if (level == null) {
            return vanilla;
        }

        return VSGameUtilsKt.toWorldCoordinates(level, vanilla);
    }

    @WrapMethod(
        method = "getPos",
        remap = false
    )
    private Vec3 wrapGetPos(float partialTick, Operation<Vec3> original) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return original.call(partialTick);

        return VSGameUtilsKt.toWorldCoordinates(level, original.call(partialTick));
    }
}
