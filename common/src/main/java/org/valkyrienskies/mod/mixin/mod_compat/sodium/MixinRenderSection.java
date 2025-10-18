package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(RenderSection.class)
public abstract class MixinRenderSection {

    @Shadow
    public abstract int getCenterX();

    @Shadow
    public abstract int getCenterY();

    @Shadow
    public abstract int getCenterZ();

    /**
     * This mixin has the same purpose with {@link org.valkyrienskies.mod.mixin.feature.fix_render_chunk_sorting.MixinRenderChunk}
     * Without this, ship chunks are considered far away from current position and always built last.
     */
    @WrapMethod(
        method = "getSquaredDistance(FFF)F"
    )
    private float wrapIfInShipyard(float x, float y, float z, Operation<Float> original) {
        final ClientShip ship = VSClientGameUtils.getClientShip(this.getCenterX(), this.getCenterY(), this.getCenterZ());
        if (ship != null) {
            final Vector3d cameraInShipyard = ship.getWorldToShip().transformPosition(x, y, z, new Vector3d());
            return original.call((float)cameraInShipyard.x, (float)cameraInShipyard.y, (float)cameraInShipyard.z);
        }
        return original.call(x, y, z);
    }
}
