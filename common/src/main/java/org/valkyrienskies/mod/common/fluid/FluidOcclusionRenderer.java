package org.valkyrienskies.mod.common.fluid;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.primitives.AABBdc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

/**
 * Uploads sparse synchronized dry-domain cells as dense bit masks for world-fluid fragment culling.
 */
public final class FluidOcclusionRenderer {
    private static final Logger LOGGER = LogManager.getLogger("Valkyrien Skies Fluid Occlusion");
    private static final int MAX_SHIPS = 9;
    private static final int BASE_TEXTURE_UNIT = 2;
    private static final int MASK_TEXTURE_WIDTH = 4096;
    private static final int MAX_MASK_VOLUME = 16 * 1024 * 1024;

    private static final ResourceLocation WATER_STILL =
        new ResourceLocation("minecraft", "block/water_still");
    private static final ResourceLocation WATER_FLOW =
        new ResourceLocation("minecraft", "block/water_flow");
    private static final ResourceLocation WATER_OVERLAY =
        new ResourceLocation("minecraft", "block/water_overlay");
    private static final ResourceLocation LAVA_STILL =
        new ResourceLocation("minecraft", "block/lava_still");
    private static final ResourceLocation LAVA_FLOW =
        new ResourceLocation("minecraft", "block/lava_flow");

    private static final Long2ObjectOpenHashMap<ShipMask> SHIP_MASKS =
        new Long2ObjectOpenHashMap<>();
    private static final LongOpenHashSet LOADED_SHIP_IDS = new LongOpenHashSet();
    private static final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ProgramHandles> PROGRAMS =
        new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final Vector3d CAMERA_IN_SHIP = new Vector3d();
    private static ClientLevel lastLevel;

    private FluidOcclusionRenderer() {
    }

    public static boolean supportsProgram(final int programId) {
        return handles(programId).enabledLocation >= 0;
    }

    public static void setupForProgram(
        final int programId,
        final ClientLevel level,
        final double cameraX,
        final double cameraY,
        final double cameraZ
    ) {
        RenderSystem.assertOnRenderThread();
        final ProgramHandles handles = handles(programId);
        if (handles.enabledLocation < 0) return;
        if (!VSGameConfig.CLIENT.getRenderFluidOcclusion()) {
            disableForProgram(programId);
            clearMasks();
            return;
        }
        if (lastLevel != level) {
            clearMasks();
            lastLevel = level;
        }

        final VsiClientShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
        final List<ClientShip> ships = new ArrayList<>();
        for (final ClientShip ship : shipWorld.getLoadedShips()) {
            ships.add(ship);
        }
        ships.sort(Comparator.comparingDouble(
            ship -> distanceSquared(ship.getRenderAABB(), cameraX, cameraY, cameraZ)
        ));

        LOADED_SHIP_IDS.clear();
        for (final ClientShip ship : ships) {
            LOADED_SHIP_IDS.add(ship.getId());
        }
        SHIP_MASKS.long2ObjectEntrySet().removeIf(entry -> {
            if (LOADED_SHIP_IDS.contains(entry.getLongKey())) return false;
            entry.getValue().close();
            return true;
        });

        int slot = 0;
        for (final ClientShip ship : ships) {
            if (slot >= MAX_SHIPS) break;
            final FluidTopologyClientCache.CachedSnapshot topology =
                FluidTopologyClientCache.get(shipWorld, ship.getId());
            if (topology == null) continue;
            final FloodedFluidClientCache.CachedSnapshot flooding =
                FloodedFluidClientCache.get(shipWorld, ship.getId());
            final ShipMask mask = SHIP_MASKS.computeIfAbsent(ship.getId(), ShipMask::new);
            if (!mask.update(topology, flooding)) continue;
            uploadShipSlot(handles, slot++, ship, mask, cameraX, cameraY, cameraZ);
        }
        for (int i = slot; i < MAX_SHIPS; i++) {
            disableShipSlot(handles, i);
        }

        uploadFluidSpriteBounds(handles);
        GL20.glUniform1f(handles.enabledLocation, slot > 0 ? 1.0F : 0.0F);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        FluidTopologyClientCache.prune(shipWorld);
        FloodedFluidClientCache.prune(shipWorld);
    }

    public static void disableForProgram(final int programId) {
        final ProgramHandles handles = PROGRAMS.get(programId);
        if (handles != null && handles.enabledLocation >= 0) {
            GL20.glUniform1f(handles.enabledLocation, 0.0F);
        }
    }

    public static void clear() {
        clearMasks();
        PROGRAMS.clear();
        lastLevel = null;
    }

    private static void clearMasks() {
        for (final ShipMask mask : SHIP_MASKS.values()) {
            mask.close();
        }
        SHIP_MASKS.clear();
    }

    private static void uploadShipSlot(
        final ProgramHandles handles,
        final int slot,
        final ClientShip ship,
        final ShipMask mask,
        final double cameraX,
        final double cameraY,
        final double cameraZ
    ) {
        final int textureUnit = BASE_TEXTURE_UNIT + slot;
        GL20.glUniform1i(handles.maskLocation[slot], textureUnit);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + textureUnit);
        GlStateManager._bindTexture(mask.textureId);

        GL20.glUniform3f(
            handles.gridSizeLocation[slot],
            mask.sizeX, mask.sizeY, mask.sizeZ
        );

        final Matrix4dc worldToShip = ship.getRenderTransform().getWorldToShip();
        worldToShip.transformPosition(cameraX, cameraY, cameraZ, CAMERA_IN_SHIP);
        GL20.glUniform3f(
            handles.cameraLocalLocation[slot],
            (float) (CAMERA_IN_SHIP.x - mask.minX),
            (float) (CAMERA_IN_SHIP.y - mask.minY),
            (float) (CAMERA_IN_SHIP.z - mask.minZ)
        );

        mask.worldToShipLinear.set(worldToShip);
        mask.worldToShipLinear.m30(0.0F).m31(0.0F).m32(0.0F);
        uploadMatrix(handles.worldToShipLocation[slot], mask.worldToShipLinear);
    }

    private static void disableShipSlot(final ProgramHandles handles, final int slot) {
        GL20.glUniform3f(handles.gridSizeLocation[slot], 0.0F, 0.0F, 0.0F);
        GL20.glUniform1i(handles.maskLocation[slot], BASE_TEXTURE_UNIT + slot);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + BASE_TEXTURE_UNIT + slot);
        GlStateManager._bindTexture(0);
    }

    private static void uploadFluidSpriteBounds(final ProgramHandles handles) {
        final Function<ResourceLocation, TextureAtlasSprite> atlas =
            Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        uploadBounds(handles.fluidBoundsLocation[0], atlas.apply(WATER_STILL));
        uploadBounds(handles.fluidBoundsLocation[1], atlas.apply(WATER_FLOW));
        uploadBounds(handles.fluidBoundsLocation[2], atlas.apply(WATER_OVERLAY));
        uploadBounds(handles.fluidBoundsLocation[3], atlas.apply(LAVA_STILL));
        uploadBounds(handles.fluidBoundsLocation[4], atlas.apply(LAVA_FLOW));
    }

    private static void uploadBounds(final int location, final TextureAtlasSprite sprite) {
        if (location < 0 || sprite == null) return;
        GL20.glUniform4f(
            location,
            sprite.getU0(), sprite.getV0(),
            sprite.getU1(), sprite.getV1()
        );
    }

    private static ProgramHandles handles(final int programId) {
        return PROGRAMS.computeIfAbsent(programId, ProgramHandles::new);
    }

    private static void uploadMatrix(final int location, final Matrix4f matrix) {
        MATRIX_BUFFER.clear();
        matrix.get(MATRIX_BUFFER);
        MATRIX_BUFFER.position(16);
        MATRIX_BUFFER.flip();
        GL20.glUniformMatrix4fv(location, false, MATRIX_BUFFER);
    }

    private static double distanceSquared(
        final AABBdc aabb,
        final double x,
        final double y,
        final double z
    ) {
        final double dx = x < aabb.minX() ? aabb.minX() - x :
            Math.max(0.0, x - aabb.maxX());
        final double dy = y < aabb.minY() ? aabb.minY() - y :
            Math.max(0.0, y - aabb.maxY());
        final double dz = z < aabb.minZ() ? aabb.minZ() - z :
            Math.max(0.0, z - aabb.maxZ());
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean isLiveTexture(final int textureId) {
        return textureId != 0 && GL11.glIsTexture(textureId);
    }

    private static final class ShipMask {
        private final long shipId;
        private final Matrix4f worldToShipLinear = new Matrix4f();
        private long topologySequence = Long.MIN_VALUE;
        private long floodingSequence = Long.MIN_VALUE;
        private int minX;
        private int minY;
        private int minZ;
        private int sizeX;
        private int sizeY;
        private int sizeZ;
        private int textureId;
        private int textureHeight;
        private boolean warnedOversized;
        private boolean hasDryCells;

        private ShipMask(final long shipId) {
            this.shipId = shipId;
        }

        private boolean update(
            final FluidTopologyClientCache.CachedSnapshot topology,
            final FloodedFluidClientCache.CachedSnapshot flooding
        ) {
            final long newTopologySequence =
                topology.getSnapshot().getStreamSequence();
            final long newFloodingSequence = flooding == null
                ? Long.MIN_VALUE
                : flooding.getSnapshot().getStreamSequence();
            if (newTopologySequence == topologySequence &&
                newFloodingSequence == floodingSequence) {
                return hasDryCells && isLiveTexture(textureId);
            }

            topologySequence = newTopologySequence;
            floodingSequence = newFloodingSequence;
            final List<VsiFluidTopologyVoxel> voxels =
                topology.getSnapshot().getVoxels();
            boolean found = false;
            int newMinX = Integer.MAX_VALUE;
            int newMinY = Integer.MAX_VALUE;
            int newMinZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (final VsiFluidTopologyVoxel voxel : voxels) {
                if (!topology.isDryDomainCell(
                    voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ(), flooding
                )) continue;
                found = true;
                newMinX = Math.min(newMinX, voxel.getPositionX());
                newMinY = Math.min(newMinY, voxel.getPositionY());
                newMinZ = Math.min(newMinZ, voxel.getPositionZ());
                maxX = Math.max(maxX, voxel.getPositionX());
                maxY = Math.max(maxY, voxel.getPositionY());
                maxZ = Math.max(maxZ, voxel.getPositionZ());
            }
            if (!found) {
                hasDryCells = false;
                return false;
            }

            final long newSizeX = (long) maxX - newMinX + 1L;
            final long newSizeY = (long) maxY - newMinY + 1L;
            final long newSizeZ = (long) maxZ - newMinZ + 1L;
            final boolean oversized =
                newSizeX > MAX_MASK_VOLUME ||
                newSizeY > MAX_MASK_VOLUME / newSizeX ||
                newSizeZ > MAX_MASK_VOLUME / (newSizeX * newSizeY);
            final long volume = oversized ? (long) MAX_MASK_VOLUME + 1L :
                newSizeX * newSizeY * newSizeZ;
            if (volume <= 0L || volume > MAX_MASK_VOLUME) {
                hasDryCells = false;
                if (!warnedOversized) {
                    warnedOversized = true;
                    LOGGER.warn(
                        "Skipping fluid-occlusion mask for ship {}: dense bounds contain {} voxels",
                        shipId, volume
                    );
                }
                return false;
            }

            minX = newMinX;
            minY = newMinY;
            minZ = newMinZ;
            sizeX = (int) newSizeX;
            sizeY = (int) newSizeY;
            sizeZ = (int) newSizeZ;
            final int wordCount = ((int) volume + 31) >>> 5;
            final int newTextureHeight =
                Math.max(1, (wordCount + MASK_TEXTURE_WIDTH - 1) / MASK_TEXTURE_WIDTH);
            final ByteBuffer bytes = BufferUtils.createByteBuffer(
                MASK_TEXTURE_WIDTH * newTextureHeight * 4
            );
            for (final VsiFluidTopologyVoxel voxel : voxels) {
                if (!topology.isDryDomainCell(
                    voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ(), flooding
                )) continue;
                final int x = voxel.getPositionX() - minX;
                final int y = voxel.getPositionY() - minY;
                final int z = voxel.getPositionZ() - minZ;
                final int index = x + sizeX * (y + sizeY * z);
                final int byteIndex = index >>> 3;
                bytes.put(byteIndex, (byte) (bytes.get(byteIndex) | (1 << (index & 7))));
            }
            bytes.position(0);
            bytes.limit(bytes.capacity());

            ensureTexture(newTextureHeight);
            uploadTexture(textureId, MASK_TEXTURE_WIDTH, newTextureHeight, bytes);
            textureHeight = newTextureHeight;
            hasDryCells = true;
            return true;
        }

        private void ensureTexture(final int requiredHeight) {
            if (isLiveTexture(textureId) && textureHeight == requiredHeight) return;
            close();
            textureId = TextureUtil.generateTextureId();
            final int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            final int previousAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
            try {
                GlStateManager._bindTexture(textureId);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
                GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
                    MASK_TEXTURE_WIDTH, requiredHeight, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null
                );
                textureHeight = requiredHeight;
            } finally {
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);
                GlStateManager._bindTexture(previousBinding);
            }
        }

        private void close() {
            if (isLiveTexture(textureId)) {
                TextureUtil.releaseTextureId(textureId);
            }
            textureId = 0;
            textureHeight = 0;
        }
    }

    private static void uploadTexture(
        final int textureId,
        final int width,
        final int height,
        final ByteBuffer bytes
    ) {
        final int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        final int previousAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            GlStateManager._bindTexture(textureId);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes
            );
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);
            GlStateManager._bindTexture(previousBinding);
        }
    }

    private static final class ProgramHandles {
        private final int enabledLocation;
        private final int[] fluidBoundsLocation = new int[5];
        private final int[] maskLocation = new int[MAX_SHIPS];
        private final int[] gridSizeLocation = new int[MAX_SHIPS];
        private final int[] cameraLocalLocation = new int[MAX_SHIPS];
        private final int[] worldToShipLocation = new int[MAX_SHIPS];

        private ProgramHandles(final int programId) {
            enabledLocation = GL20.glGetUniformLocation(programId, "VsFluidOcclusionEnabled");
            for (int i = 0; i < fluidBoundsLocation.length; i++) {
                fluidBoundsLocation[i] =
                    GL20.glGetUniformLocation(programId, "VsFluidOcclusionUv" + i);
            }
            for (int i = 0; i < MAX_SHIPS; i++) {
                maskLocation[i] =
                    GL20.glGetUniformLocation(programId, "VsFluidOcclusionMask" + i);
                gridSizeLocation[i] =
                    GL20.glGetUniformLocation(programId, "VsFluidOcclusionGridSize" + i);
                cameraLocalLocation[i] =
                    GL20.glGetUniformLocation(programId, "VsFluidOcclusionCameraLocal" + i);
                worldToShipLocation[i] =
                    GL20.glGetUniformLocation(programId, "VsFluidOcclusionWorldToShip" + i);
            }
        }
    }
}
