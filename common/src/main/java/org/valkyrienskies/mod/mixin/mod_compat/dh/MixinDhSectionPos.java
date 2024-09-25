package org.valkyrienskies.mod.mixin.mod_compat.dh;

import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import net.minecraft.client.Minecraft;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(DhSectionPos.class)
public abstract class MixinDhSectionPos {

    @Inject(method = "getManhattanBlockDistance", at = @At("HEAD"), cancellable = true, remap = false)
    private static void getManhattanBlockDistance(final long cp, final DhBlockPos2D secondPos, final CallbackInfoReturnable<Integer> cir) {
        final var bpX = DhSectionPos.getCenterBlockPosX(cp);
        final var bpZ = DhSectionPos.getCenterBlockPosZ(cp);

        final var level = Minecraft.getInstance().level;

        final var tr1 = VSGameUtilsKt.toWorldCoordinates(level, bpX, 0.0, bpZ, new Vector3d());
        final var tr2 = VSGameUtilsKt.toWorldCoordinates(level, secondPos.x, 0.0, secondPos.z, new Vector3d());

        final var dist = Math.abs(tr1.x - tr2.x) + Math.abs(tr1.z - tr2.z);

        cir.setReturnValue((int) dist);
    }

}
