package org.valkyrienskies.mod.api.render;

import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.joml.Matrix4f;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.render.batched.ShipPortraitRenderer;

/**
 * Off-screen rendering of one ship into the framebuffer currently bound by the caller.
 *
 * <p>Call on the render thread during level rendering. Model-view and projection are borrowed for
 * the call. Geometry is sourced from the official improved-renderer cache and is independent of
 * whether vanilla, Sodium, or another world backend is active.
 */
@Experimental
public final class ShipPortraits {
    private ShipPortraits() {
    }

    /**
     * Draws a ship as seen from a point in its own shipyard coordinates.
     *
     * @param opaqueOnly skip the translucent layer
     * @param fullBright use maximum light instead of the shipyard's block and sky light
     * @param renderBlockEntities draw block entities after the ship's block geometry
     * @return whether anything was drawn
     */
    public static boolean render(final ClientLevel level, final ClientShip ship,
        final Matrix4f modelView, final Matrix4f projection,
        final double cameraX, final double cameraY, final double cameraZ,
        final boolean opaqueOnly, final boolean fullBright,
        final boolean renderBlockEntities) {
        return ShipPortraitRenderer.render(level, ship, modelView, projection,
            cameraX, cameraY, cameraZ, opaqueOnly, fullBright, renderBlockEntities);
    }

    /** Stop retaining geometry for a portrait that is no longer displayed. */
    public static void forget(final long shipId) {
        ShipPortraitRenderer.forget(shipId);
    }
}
