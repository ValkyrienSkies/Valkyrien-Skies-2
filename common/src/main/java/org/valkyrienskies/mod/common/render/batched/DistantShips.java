package org.valkyrienskies.mod.common.render.batched;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.distant.DistantShipSnapshots;
import org.valkyrienskies.mod.common.networking.DistantShipTransforms;
import org.valkyrienskies.mod.common.networking.PacketDistantShip;
import org.valkyrienskies.mod.common.networking.PacketDistantShipTransforms;
import org.valkyrienskies.mod.common.networking.PacketRemoveDistantShips;
import org.valkyrienskies.mod.common.networking.PacketShipRenderDistance;
import org.valkyrienskies.mod.compat.lod.LodCompat;

/**
 * Client side of distant ships: render-only copies of ships beyond the ship load distance, out to the player's view
 * distance (vanilla render distance or LOD distance, whichever is larger).
 *
 * <p>A copy is not drawn while its loaded ship draws. The server sends copies of loaded ships near the edge of the
 * load distance ahead of time, so when such a ship unloads its copy takes over in the same frame.
 *
 * <p>Everything here runs on the client thread. Packet handlers must hand their packets over to it.
 */
public final class DistantShips {

    private static final Logger LOGGER = LogManager.getLogger("Valkyrien Skies Distant Ships");

    private static final long REMOVE_GRACE_NANOS = 5_000_000_000L;
    private static final long OTHER_RENDERER_DELAY_NANOS = 1_000_000_000L;
    private static final int MESH_BUILDS_PER_FRAME = 1;
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final int VIEW_DISTANCE_CHECK_INTERVAL_TICKS = 20;

    private static final Long2ObjectOpenHashMap<DistantShipRenderObject> SHIPS = new Long2ObjectOpenHashMap<>();
    private static final Long2LongOpenHashMap LOADED_SINCE = new Long2LongOpenHashMap();
    private static final DistantShipBlockView VIEW = new DistantShipBlockView();

    private static ClientLevel level;
    private static long frame = 1;

    private static int sentDistance = -1;
    private static String sentDimension;
    private static ClientLevel sentLevel;
    private static int ticksUntilViewDistanceCheck;

    private DistantShips() {
    }

    // region Packets

    public static void onShip(final PacketDistantShip packet) {
        final ClientLevel current = Minecraft.getInstance().level;
        if (current == null || !packet.getDimension().equals(VSGameUtilsKt.getDimensionId(current))) {
            return;
        }
        syncLevel(current);
        if (!VSGameConfig.CLIENT.getDistantShips().getRenderDistantShips()) {
            return;
        }
        final DistantShipSnapshots.Decoded snapshot = new DistantShipSnapshots.Decoded(
            new Long2ObjectOpenHashMap<>(), new Long2ObjectOpenHashMap<>(), new Long2ObjectOpenHashMap<>());
        try {
            if (!DistantShipSnapshots.decode(packet.getSnapshot(),
                current.registryAccess().registryOrThrow(Registries.BIOME), snapshot)) {
                return;
            }
        } catch (final RuntimeException e) {
            LOGGER.warn("Could not read distant ship {}", packet.getShipId(), e);
            return;
        }
        final double[] transform = packet.getTransform();
        if (transform.length < DistantShipTransforms.SIZE) {
            return;
        }
        DistantShipRenderObject ship = SHIPS.get(packet.getShipId());
        if (ship == null) {
            ship = new DistantShipRenderObject(packet.getShipId(), packet.getDimension());
            SHIPS.put(packet.getShipId(), ship);
            ship.setTransform(transform, 0);
        } else {
            ship.blendTo(transform, 0, packet.getTransformIntervalTicks() * NANOS_PER_TICK);
        }
        ship.removeRequestedNanos = -1;
        ship.setSnapshot(packet.getRevision(), packet.getShipAABB(), snapshot);
        ship.updatePose(0);
    }

    public static void onTransforms(final PacketDistantShipTransforms packet) {
        final ClientLevel current = Minecraft.getInstance().level;
        if (current == null || SHIPS.isEmpty()
            || !packet.getDimension().equals(VSGameUtilsKt.getDimensionId(current))) {
            return;
        }
        final long[] ids = packet.getShipIds();
        final double[] transforms = packet.getTransforms();
        final long blendNanos = packet.getIntervalTicks() * NANOS_PER_TICK;
        for (int i = 0; i < ids.length; i++) {
            final int offset = i * DistantShipTransforms.SIZE;
            if (offset + DistantShipTransforms.SIZE > transforms.length) {
                break;
            }
            final DistantShipRenderObject ship = SHIPS.get(ids[i]);
            if (ship != null) {
                ship.blendTo(transforms, offset, blendNanos);
            }
        }
    }

    public static void onRemove(final PacketRemoveDistantShips packet) {
        final long now = System.nanoTime();
        for (final long id : packet.getShipIds()) {
            final DistantShipRenderObject ship = SHIPS.get(id);
            if (ship != null && ship.removeRequestedNanos < 0) {
                ship.removeRequestedNanos = now;
            }
        }
    }

    // endregion

    /** Tell the server how far this player can see, when that changes. Call every client tick. */
    public static void tick(final Minecraft minecraft) {
        final ClientLevel current = minecraft.level;
        if (current == null || minecraft.getConnection() == null) {
            return;
        }
        syncLevel(current);
        final String dimension = VSGameUtilsKt.getDimensionId(current);
        final int distance = viewDistanceBlocks(minecraft);
        if (!levelChanged && distance == sentDistance && dimension.equals(sentDimension)) {
            return;
        }
        sentDistance = distance;
        sentLevel = current;
        sentDimension = dimension;
        ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking()
            .sendToServer(new PacketShipRenderDistance(distance, dimension));
    }

    public static int viewDistanceBlocks(final Minecraft minecraft) {
        final var config = VSGameConfig.CLIENT.getDistantShips();
        if (!config.getRenderDistantShips()) {
            return 0;
        }
        if (config.getShipRenderDistance() > 0) {
            return config.getShipRenderDistance() * 16;
        }
        return Math.max(minecraft.options.getEffectiveRenderDistance() * 16, LodCompat.renderDistanceBlocks());
    }

    /** Squared distance from the camera to the furthest corner of any distant ship, for the projection's far plane. */
    public static double farthestCornerDistanceSq(final Vec3 camera) {
        double farthest = 0.0;
        for (final DistantShipRenderObject ship : SHIPS.values()) {
            farthest = Math.max(farthest, VSClientGameUtils.farthestCornerDistanceSq(
                ship.renderWorldAABB(), camera.x, camera.y, camera.z));
        }
        return farthest;
    }

    /**
     * Add the distant ships to draw this frame to {@code out}, building at most one mesh.
     *
     * @param loaded the batched renderer's loaded ships, by ship id
     */
    static void collect(final ClientLevel current, final BlockRenderDispatcher dispatcher,
        final ShipSectionCompiler compiler, final Long2ObjectMap<ShipRenderObject> loaded,
        final List<BatchedShipDrawable> out) {
        syncLevel(current);
        if (SHIPS.isEmpty()) {
            return;
        }
        if (!VSGameConfig.CLIENT.getDistantShips().getRenderDistantShips()) {
            clear();
            return;
        }
        frame++;
        final long now = System.nanoTime();
        final var loadedShips = VSGameUtilsKt.getShipObjectWorld(current).getLoadedShips();
        int builds = MESH_BUILDS_PER_FRAME;

        final var iterator = SHIPS.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            final DistantShipRenderObject ship = iterator.next().getValue();
            final long id = ship.id;
            final ClientShip loadedShip = loadedShips.getById(id);
            final boolean loadedDraws = loadedShipDraws(id, loadedShip, loaded.get(id), now);
            ship.suppressed = loadedDraws;

            if (ship.removeRequestedNanos >= 0
                && (loadedDraws || loadedShip == null || now - ship.removeRequestedNanos > REMOVE_GRACE_NANOS)) {
                ship.close();
                iterator.remove();
                continue;
            }
            if (loadedDraws) {
                continue;
            }
            ship.updatePose(frame);
            if (ship.needsMesh() && builds > 0) {
                builds--;
                ship.buildMesh(current, dispatcher, compiler, VIEW);
            }
            if (ship.getMesh() != null) {
                out.add(ship);
            }
        }
        if (!LOADED_SINCE.isEmpty()) {
            final var loadedIterator = LOADED_SINCE.keySet().iterator();
            while (loadedIterator.hasNext()) {
                if (loadedShips.getById(loadedIterator.nextLong()) == null) {
                    loadedIterator.remove();
                }
            }
        }
    }

    /** Whether the loaded counterpart of a distant ship is drawing, so the copy can step aside. */
    private static boolean loadedShipDraws(final long id, final ClientShip loadedShip,
        final ShipRenderObject batched, final long now) {
        if (batched != null) {
            return !batched.isEmpty();
        }
        if (loadedShip == null || ShipRendererKt.getUsesBatchedRenderer(loadedShip)) {
            return false;
        }
        if (!LOADED_SINCE.containsKey(id)) {
            LOADED_SINCE.put(id, now);
        }
        return now - LOADED_SINCE.get(id) > OTHER_RENDERER_DELAY_NANOS;
    }

    /** Drop every distant ship when the client level changes. */
    private static void syncLevel(final ClientLevel current) {
        if (level != current) {
            clear();
            level = current;
        }
    }

    /** Free the GPU meshes, keeping the blocks so they can be built again. */
    static void freeMeshes() {
        for (final DistantShipRenderObject ship : SHIPS.values()) {
            ship.freeMesh();
        }
    }

    /** Drop all distant ships. Call on the client thread. */
    public static void clear() {
        for (final DistantShipRenderObject ship : SHIPS.values()) {
            ship.close();
        }
        SHIPS.clear();
        LOADED_SINCE.clear();
        level = null;
    }

    /** Forget what was sent to the server, so the next tick reports the view distance again. */
    public static void onDisconnect() {
        clear();
        sentDistance = -1;
        sentDimension = null;
        sentLevel = null;
        ticksUntilViewDistanceCheck = 0;
    }
}
