package org.valkyrienskies.mod.mixin.mod_compat.common_create.packets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsInputPacket;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ControlsInputPacket.class)
public abstract class MixinControlsInputPacket {
    @WrapOperation(
            method = "lambda$handle$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"
            )
    )
    private boolean wrapCloserThan(
        final Vec3 instance, final Position position, final double d, final Operation<Boolean> original,
        @Local final Level level
    ) {
        return original.call(VSGameUtilsKt.toWorldCoordinates(level, instance), position, d);
    }
}
