package org.valkyrienskies.mod.mixin.mod_compat.dh;

import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import net.minecraft.client.Minecraft;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(DhBlockPos.class)
public abstract class MixinDhBlockPos {

    @Unique
    private static Vector3d vs$toWorld(final DhBlockPos bp) {
        final var level = Minecraft.getInstance().level;
        return VSGameUtilsKt.toWorldCoordinates(level, bp.getX(), bp.getY(), bp.getZ());
    }

    @Inject(at = @At("HEAD"), method = "getManhattanDistance", cancellable = true, remap = false)
    public void getManhattanDistance(final DhBlockPos otherPos, final CallbackInfoReturnable<Integer> cir) {
        final var self = (DhBlockPos) (Object) this;

        final var a = vs$toWorld(self);
        final var b = vs$toWorld(otherPos);

        final var r = Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z);

        cir.setReturnValue((int) r);
    }

}
