package org.valkyrienskies.mod.forge.mixin.compat.mffs;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.su5ed.mffs.util.FrequencyGrid;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(FrequencyGrid.class)
public abstract class MixinFrequencyGrid {
    @WrapOperation(
        method = "lambda$get$2",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/Vec3i;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
        )
    )
    private static boolean vs$closerThan(final Vec3i instance, final Vec3i vec3i, final double d, final Operation<Boolean> original, @Local(argsOnly = true) final Level level) {
        if (level == null) {
            return original.call(instance, vec3i, d);
        }

        Vec3i origin = instance;
        Vec3i target = vec3i;

        if (VSGameUtilsKt.getShipObjectManagingPos(level, instance) instanceof final LoadedServerShip ship) {
            final Vec3 temp = VectorConversionsMCKt.toMinecraft(ship.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOMLD(origin)));
            origin = new Vec3i((int) temp.x, (int) temp.y, (int) temp.z);
        }

        if (VSGameUtilsKt.getShipObjectManagingPos(level, vec3i) instanceof final LoadedServerShip ship) {
            final Vec3 temp = VectorConversionsMCKt.toMinecraft(ship.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOMLD(target)));
            target = new Vec3i((int) temp.x, (int) temp.y, (int) temp.z);
        }

        return original.call(origin, target, d);
    }
}
