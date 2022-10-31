package org.valkyrienskies.mod.mixin.mod_compat.optifine;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LevelRenderer.class)
public class MixinLevelRendererOptifine {
    @Shadow
    private ClientLevel level;

    /**
     * Fix the distance to render chunks, so that MC doesn't think ship chunks are too far away
     */
    @Redirect(
        method = "setupRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    private double includeShipChunksInNearChunks(final Vec3 vec1, final Vec3 vec2) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(
            level, vec1.x(), vec1.y(), vec1.z(), vec2.x(), vec2.y(), vec2.z()
        );
    }
}
