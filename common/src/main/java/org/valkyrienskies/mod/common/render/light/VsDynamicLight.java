package org.valkyrienskies.mod.common.render.light;

import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.compat.sodium.light.VsShipEmitterList;
import org.valkyrienskies.mod.compat.sodium.light.VsShipLightStorage;
import org.valkyrienskies.mod.compat.sodium.light.VsShipOccluderList;
import org.valkyrienskies.mod.compat.sodium.light.VsWorldFromShipLightStorage;

public final class VsDynamicLight {

    public static final int LIGHT_SECTIONS_TEXTURE_UNIT = 6;
    public static final int LIGHT_LUT_TEXTURE_UNIT = 7;
    public static final int WORLD_FROM_SHIP_SECTIONS_TEXTURE_UNIT = 10;
    public static final int WORLD_FROM_SHIP_LUT_TEXTURE_UNIT = 11;
    public static final int SHIP_EMITTER_LIST_TEXTURE_UNIT = 12;
    public static final int SHIP_OCCLUDER_LIST_TEXTURE_UNIT = 13;

    private static VsShipLightStorage lightStorage;
    private static VsWorldFromShipLightStorage worldFromShipStorage;
    private static VsShipEmitterList shipEmitterList;
    private static VsShipOccluderList shipOccluderList;

    private VsDynamicLight() {
    }

    public static VsShipLightStorage getLightStorage() {
        if (lightStorage == null) {
            lightStorage = new VsShipLightStorage();
        }
        return lightStorage;
    }

    public static VsWorldFromShipLightStorage getWorldFromShipStorage() {
        if (worldFromShipStorage == null) {
            worldFromShipStorage = new VsWorldFromShipLightStorage();
        }
        return worldFromShipStorage;
    }

    public static VsShipEmitterList getShipEmitterList() {
        if (shipEmitterList == null) {
            shipEmitterList = new VsShipEmitterList();
        }
        return shipEmitterList;
    }

    public static VsShipOccluderList getShipOccluderList() {
        if (shipOccluderList == null) {
            shipOccluderList = new VsShipOccluderList();
        }
        return shipOccluderList;
    }

    public static void deleteStorages() {
        if (lightStorage != null) {
            lightStorage.delete();
            lightStorage = null;
        }
        if (worldFromShipStorage != null) {
            worldFromShipStorage.delete();
            worldFromShipStorage = null;
        }
        if (shipEmitterList != null) {
            shipEmitterList.delete();
            shipEmitterList = null;
        }
        if (shipOccluderList != null) {
            shipOccluderList.delete();
            shipOccluderList = null;
        }
    }

    public static void populateWorldLightForBatched(final ClientLevel level) {
        if (level == null) {
            return;
        }
        final VsShipLightStorage light = getLightStorage();
        light.beginFrame();
        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (!ShipRendererKt.getUsesBatchedRenderer(ship)) {
                continue;
            }
            final AABBdc aabb = ship.getRenderAABB();
            if (aabb != null) {
                light.requestSectionsInAabb(level,
                    aabb.minX(), aabb.minY(), aabb.minZ(),
                    aabb.maxX(), aabb.maxY(), aabb.maxZ());
            }
        }
        light.pruneUnused();
        light.upload();
    }

    public static void populateShipEmittersForBatched(final ClientLevel level) {
        if (level == null) {
            return;
        }
        final VsShipEmitterList emitters = getShipEmitterList();
        emitters.beginFrame();
        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (ShipRendererKt.getUsesBatchedRenderer(ship)) {
                emitters.populateFromShip(level, ship);
            }
        }
        emitters.upload();
    }
}
