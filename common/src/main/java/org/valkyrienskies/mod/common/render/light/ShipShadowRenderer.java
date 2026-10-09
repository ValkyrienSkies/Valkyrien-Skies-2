package org.valkyrienskies.mod.common.render.light;

import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.primitives.AABBi;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBic;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

public final class ShipShadowRenderer {
    private static final int MAX_BOXES = 512;
    private static final int TEXTURE_UNIT = 14;
    private static final int MAX_CELLS_PER_SHIP = 524288;
    private static final ByteBuffer DATA = BufferUtils.createByteBuffer(MAX_BOXES * 48);
    private record Geometry(List<AABBd> boxes, boolean dynamic) { }
    private static final ShipGeometryCache<Geometry> GEOMETRY = new ShipGeometryCache<>();
    private static final Quaterniond ROTATION = new Quaterniond();
    private static final Vector3d SCALE = new Vector3d();
    private static final Vector3d CENTER = new Vector3d();
    private static int buffer;
    private static int texture;
    private static int count;

    private ShipShadowRenderer() {
    }

    public static void prepare(final ClientLevel level) {
        GEOMETRY.beginFrame(level);
        count = 0;
        DATA.clear();
        if (level == null || !VSGameConfig.CLIENT.isShipToWorldLightingEnabled()
            || !level.dimensionType().hasSkyLight()) {
            GEOMETRY.endFrame();
            return;
        }
        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (count >= MAX_BOXES) break;
            final List<AABBd> boxes = GEOMETRY.get(ship.getId(), ship.getShipAABB(),
                ship.getActiveChunksSet(), level.getGameTime(), Geometry::dynamic,
                () -> collectGeometry(level, ship)).boxes();
            if (boxes.isEmpty()) continue;
            final var transform = ship.getRenderTransform().getShipToWorld();
            final Quaterniond rotation = transform.getNormalizedRotation(ROTATION);
            final Vector3d scale = transform.getScale(SCALE);
            for (final AABBd box : boxes) {
                if (count >= MAX_BOXES) break;
                final Vector3d center = transform.transformPosition(CENTER.set(
                    (box.minX + (double) box.maxX) * 0.5,
                    (box.minY + (double) box.maxY) * 0.5,
                    (box.minZ + (double) box.maxZ) * 0.5));
                DATA.putFloat((float) center.x).putFloat((float) center.y).putFloat((float) center.z).putFloat(0);
                DATA.putFloat((float) ((box.maxX - box.minX) * scale.x * 0.5));
                DATA.putFloat((float) ((box.maxY - box.minY) * scale.y * 0.5));
                DATA.putFloat((float) ((box.maxZ - box.minZ) * scale.z * 0.5)).putFloat(0);
                DATA.putFloat((float) rotation.x).putFloat((float) rotation.y)
                    .putFloat((float) rotation.z).putFloat((float) rotation.w);
                count++;
            }
        }
        GEOMETRY.endFrame();
        DATA.flip();
        ensureTexture();
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, count == 0 ? 48L : DATA.remaining(), GL15.GL_STREAM_DRAW);
        if (count > 0) GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0, DATA);
        // Attach the new data store after the buffer allocation.
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + TEXTURE_UNIT);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, buffer);
        GlStateManager._activeTexture(active);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
    }

    public static void invalidate(final long shipId) {
        GEOMETRY.invalidate(shipId);
    }

    static List<AABBd> collect(final LevelAccessor level, final ClientShip ship) {
        return collectGeometry(level, ship).boxes();
    }

    private static Geometry collectGeometry(final LevelAccessor level, final ClientShip ship) {
        final AABBic bounds = ship.getShipAABB();
        if (bounds == null) return new Geometry(List.of(), false);
        final List<AABBi> full = new ArrayList<>();
        final List<AABBd> partial = new ArrayList<>();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final int[] visited = {0};
        final boolean[] dynamic = {false};
        ship.getActiveChunksSet().forEach((cx, cz) -> {
            if (visited[0] >= MAX_CELLS_PER_SHIP) return;
            final var chunk = level.getChunk(cx, cz,
                net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
            if (chunk == null) return;
            for (int sy = Math.max(level.getMinSection(), bounds.minY() >> 4);
                sy <= Math.min(level.getMaxSection() - 1, (bounds.maxY() - 1) >> 4); sy++) {
                final var section = chunk.getSection(chunk.getSectionIndex(sy << 4));
                if (section.hasOnlyAir()) continue;
                final BitSet cells = new BitSet(4096);
                scan:
                for (int y = Math.max(bounds.minY(), sy << 4); y < Math.min(bounds.maxY(), (sy + 1) << 4); y++)
                    for (int z = Math.max(bounds.minZ(), cz << 4); z < Math.min(bounds.maxZ(), (cz + 1) << 4); z++)
                        for (int x = Math.max(bounds.minX(), cx << 4); x < Math.min(bounds.maxX(), (cx + 1) << 4); x++) {
                            if (++visited[0] > MAX_CELLS_PER_SHIP) break scan;
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
                            } else if (partial.size() < MAX_BOXES) {
                                for (final var box : shape.toAabbs()) {
                                    partial.add(new AABBd(box.minX + x, box.minY + y, box.minZ + z,
                                        box.maxX + x, box.maxY + y, box.maxZ + z));
                                }
                            }
                        }
                for (final AABBi box : ShipShadowBoxes.merge(cells, 16, 16, 16, MAX_BOXES)) {
                    full.add(new AABBi(box.minX + (cx << 4), box.minY + (sy << 4), box.minZ + (cz << 4),
                        box.maxX + (cx << 4), box.maxY + (sy << 4), box.maxZ + (cz << 4)));
                }
                if (visited[0] >= MAX_CELLS_PER_SHIP) break;
            }
        });
        final List<AABBi> boxes = ShipShadowBoxes.joinSections(full, MAX_BOXES);
        final List<AABBd> result = new ArrayList<>(boxes.size() + partial.size());
        for (final AABBi box : boxes) result.add(new AABBd(
            box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        result.addAll(partial);
        return new Geometry(result, dynamic[0]);
    }

    public static void bind(final int programId) {
        setStrength(programId, "u_VsShipShadowStrength", VSGameConfig.CLIENT.getShipShadowStrength());
        setStrength(programId, "u_VsShipShadeStrength", VSGameConfig.CLIENT.getShipShadeStrength());
        final int softness = GL20.glGetUniformLocation(programId, "u_VsShipShadowSoftness");
        if (softness >= 0) {
            GL20.glUniform1f(softness, (float) Math.max(0.0, Math.min(4.0, VSGameConfig.CLIENT.getShipShadowSoftness())));
        }
        final int blurGrowth = GL20.glGetUniformLocation(programId, "u_VsShipShadowBlurGrowth");
        if (blurGrowth >= 0) {
            GL20.glUniform1f(blurGrowth,
                (float) Math.max(0.0, Math.min(0.5, VSGameConfig.CLIENT.getShipShadowBlurGrowth())));
        }
        final int sampler = GL20.glGetUniformLocation(programId, "u_VsShipShadowBoxes");
        if (sampler < 0) return;
        ensureTexture();
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + TEXTURE_UNIT);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        GlStateManager._activeTexture(active);
        GL20.glUniform1i(sampler, TEXTURE_UNIT);
        final int countLocation = GL20.glGetUniformLocation(programId, "u_VsShipShadowCount");
        if (countLocation >= 0) GL20.glUniform1i(countLocation, count);
    }

    private static void setStrength(final int programId, final String name, final double value) {
        final int location = GL20.glGetUniformLocation(programId, name);
        if (location >= 0) GL20.glUniform1f(location, (float) Math.max(0.0, Math.min(1.0, value)));
    }

    private static void ensureTexture() {
        if (buffer != 0) return;
        buffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, 48L, GL15.GL_STREAM_DRAW);
        texture = GL11.glGenTextures();
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, buffer);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
    }

    public static void delete() {
        if (texture != 0) GL11.glDeleteTextures(texture);
        if (buffer != 0) GL15.glDeleteBuffers(buffer);
        texture = 0;
        buffer = 0;
        count = 0;
        GEOMETRY.clear();
    }
}
