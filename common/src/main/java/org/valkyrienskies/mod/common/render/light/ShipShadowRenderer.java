package org.valkyrienskies.mod.common.render.light;

import java.util.BitSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.primitives.AABBi;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBic;
import org.lwjgl.opengl.GL20;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

public final class ShipShadowRenderer {
    private static final ShipShadowInstances INSTANCES = new ShipShadowInstances();
    private static final ShipShadowMap MAP = new ShipShadowMap();
    private static final ShipShadowMapLayout LAYOUT = new ShipShadowMapLayout();
    private static final ShipShadowFrame FRAME = new ShipShadowFrame();
    private record Geometry(List<AABBd> boxes, boolean dynamic, AABBd bounds) { }
    private static final ShipGeometryCache<Geometry> GEOMETRY = new ShipGeometryCache<>();
    private static final Vector3d CENTER = new Vector3d();
    private static int count;
    private static boolean ready;
    private static ClientLevel currentLevel;

    private ShipShadowRenderer() {
    }

    public static void prepare(final ClientLevel level) {
        if (currentLevel != level) {
            delete();
            currentLevel = level;
        }
        GEOMETRY.beginFrame(level);
        if (level == null || !VSGameConfig.CLIENT.getShipShadows()
            || VSGameConfig.CLIENT.getShipShadowStrength() <= 0.0
            || !level.dimensionType().hasSkyLight()) {
            count = 0;
            if (ready) MAP.close();
            ready = false;
            FRAME.clear();
            INSTANCES.clear();
            GEOMETRY.endFrame();
            return;
        }
        double minY = level.getMinBuildHeight() - 64.0;
        double maxY = level.getMaxBuildHeight();
        FRAME.begin();
        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            final Geometry geometry = GEOMETRY.get(ship.getId(), ship.getShipAABB(),
                ship.getActiveChunksSet(), level.getGameTime(), Geometry::dynamic,
                () -> collectGeometry(level, ship));
            final List<AABBd> boxes = geometry.boxes();
            FRAME.add(ship.getId(), boxes, ship.getRenderTransform().getShipToWorld());
            final AABBd bounds = geometry.bounds();
            if (bounds != null && !boxes.isEmpty()) {
                for (int corner = 0; corner < 8; corner++) {
                    ship.getRenderTransform().getShipToWorld().transformPosition(CENTER.set(
                        (corner & 1) == 0 ? bounds.minX : bounds.maxX,
                        (corner & 2) == 0 ? bounds.minY : bounds.maxY,
                        (corner & 4) == 0 ? bounds.minZ : bounds.maxZ));
                    minY = Math.min(minY, CENTER.y);
                    maxY = Math.max(maxY, CENTER.y);
                }
            }
        }
        GEOMETRY.endFrame();
        final boolean frameChanged = FRAME.finish();
        final var minecraft = Minecraft.getInstance();
        final var camera = minecraft.gameRenderer.getMainCamera().getPosition();
        final double oldX = LAYOUT.originX, oldY = LAYOUT.originY, oldZ = LAYOUT.originZ;
        final boolean layoutChanged = LAYOUT.update(camera.x, camera.y, camera.z, minY, maxY,
            minecraft.options.getEffectiveRenderDistance());
        final boolean originChanged = oldX != LAYOUT.originX || oldY != LAYOUT.originY || oldZ != LAYOUT.originZ;
        if (!frameChanged && !layoutChanged && ready) return;
        if (!frameChanged && !originChanged && ready) {
            MAP.renderLocal(null, null, count, LAYOUT);
            return;
        }
        final boolean geometryChanged = FRAME.geometryChanged() || !ready;
        INSTANCES.update(FRAME.entries(), geometryChanged, LAYOUT);
        count = INSTANCES.count();
        if (count == 0) {
            INSTANCES.clear();
            MAP.close();
            ready = false;
            return;
        }
        MAP.renderLocal(geometryChanged ? INSTANCES.boxes() : null, INSTANCES.transforms(), count, LAYOUT);
        ready = true;
    }

    public static void invalidate(final long shipId) {
        GEOMETRY.invalidate(shipId);
    }

    static List<AABBd> collect(final LevelAccessor level, final ClientShip ship) {
        return collectGeometry(level, ship).boxes();
    }

    private static Geometry collectGeometry(final LevelAccessor level, final ClientShip ship) {
        final AABBic bounds = ship.getShipAABB();
        if (bounds == null) return new Geometry(List.of(), false, null);
        final List<AABBi> full = new ArrayList<>();
        final List<AABBd> partial = new ArrayList<>();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final boolean[] dynamic = {false};
        ship.getActiveChunksSet().forEach((cx, cz) -> {
            final var chunk = level.getChunk(cx, cz,
                net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
            if (chunk == null) return;
            for (int sy = Math.max(level.getMinSection(), bounds.minY() >> 4);
                sy <= Math.min(level.getMaxSection() - 1, (bounds.maxY() - 1) >> 4); sy++) {
                final var section = chunk.getSection(chunk.getSectionIndex(sy << 4));
                if (section.hasOnlyAir()) continue;
                final BitSet cells = new BitSet(4096);
                for (int y = Math.max(bounds.minY(), sy << 4); y < Math.min(bounds.maxY(), (sy + 1) << 4); y++)
                    for (int z = Math.max(bounds.minZ(), cz << 4); z < Math.min(bounds.maxZ(), (cz + 1) << 4); z++)
                        for (int x = Math.max(bounds.minX(), cx << 4); x < Math.min(bounds.maxX(), (cx + 1) << 4); x++) {
                            pos.set(x, y, z);
                            final BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                            // Keep the tick refresh for shapes that can depend on the world.
                            if (state.getBlock().hasDynamicShape()
                                || !state.getBlock().getClass().getName().startsWith("net.minecraft.")) {
                                dynamic[0] = true;
                            }
                            if (state.isAir() || (!state.canOcclude() && state.getLightBlock(level, pos) < 15)) continue;
                            final var shape = state.getShape(level, pos);
                            if (Block.isShapeFullBlock(shape)) {
                                cells.set(((y & 15) * 16 + (z & 15)) * 16 + (x & 15));
                            } else {
                                for (final var box : shape.toAabbs()) {
                                    partial.add(new AABBd(box.minX + x, box.minY + y, box.minZ + z,
                                        box.maxX + x, box.maxY + y, box.maxZ + z));
                                }
                            }
                        }
                for (final AABBi box : ShipShadowBoxes.merge(cells, 16, 16, 16, Integer.MAX_VALUE)) {
                    full.add(new AABBi(box.minX + (cx << 4), box.minY + (sy << 4), box.minZ + (cz << 4),
                        box.maxX + (cx << 4), box.maxY + (sy << 4), box.maxZ + (cz << 4)));
                }
            }
        });
        final List<AABBi> boxes = ShipShadowBoxes.joinSections(full, Integer.MAX_VALUE);
        final List<AABBd> result = new ArrayList<>(boxes.size() + partial.size());
        for (final AABBi box : boxes) result.add(new AABBd(
            box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        result.addAll(partial);
        final AABBd shapeBounds = result.isEmpty() ? null : new AABBd(result.get(0));
        if (shapeBounds != null) for (final AABBd box : result) {
            shapeBounds.minX = Math.min(shapeBounds.minX, box.minX);
            shapeBounds.minY = Math.min(shapeBounds.minY, box.minY);
            shapeBounds.minZ = Math.min(shapeBounds.minZ, box.minZ);
            shapeBounds.maxX = Math.max(shapeBounds.maxX, box.maxX);
            shapeBounds.maxY = Math.max(shapeBounds.maxY, box.maxY);
            shapeBounds.maxZ = Math.max(shapeBounds.maxZ, box.maxZ);
        }
        return new Geometry(result, dynamic[0], shapeBounds);
    }

    public static void bind(final int programId) {
        final boolean enabled = VSGameConfig.CLIENT.getShipShadows() && ready;
        setStrength(programId, "u_VsShipShadowStrength", enabled ? VSGameConfig.CLIENT.getShipShadowStrength() : 0.0);
        setStrength(programId, "u_VsShipShadeStrength", VSGameConfig.CLIENT.getShipShadeStrength());
        final int softness = GL20.glGetUniformLocation(programId, "u_VsShipShadowSoftness");
        if (softness >= 0) {
            GL20.glUniform1f(softness, (float) Math.max(0.0, Math.min(4.0, VSGameConfig.CLIENT.getShipShadowSoftness())));
        }
        MAP.bind(programId, LAYOUT, enabled);
    }

    private static void setStrength(final int programId, final String name, final double value) {
        final int location = GL20.glGetUniformLocation(programId, name);
        if (location >= 0) GL20.glUniform1f(location, (float) Math.max(0.0, Math.min(1.0, value)));
    }

    public static void delete() {
        MAP.close();
        count = 0;
        ready = false;
        currentLevel = null;
        INSTANCES.clear();
        FRAME.clear();
        GEOMETRY.clear();
    }
}
