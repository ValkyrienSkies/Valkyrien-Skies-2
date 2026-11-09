package org.valkyrienskies.mod.mixinducks.mod_compat.sodium;

import java.util.WeakHashMap;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import org.valkyrienskies.core.api.ships.ClientShip;

public interface RenderSectionManagerDuck {

    WeakHashMap<ClientShip, SortedRenderLists> vs_getShipRenderLists();

    void vs$markShipRenderListsDirty();

    void vs$invalidateShipSectionCache(ClientShip ship);

    void vs$updateShipRenderLists(Camera camera, Viewport viewport, int frame, boolean spectator);
}
