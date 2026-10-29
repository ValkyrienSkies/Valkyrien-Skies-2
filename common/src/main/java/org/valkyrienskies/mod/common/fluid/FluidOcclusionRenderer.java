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
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

/**
 * Uploads sparse synchronized dry-domain cells as dense bit masks for world-fluid fragment culling.
 */
public final class FluidOcclusionRenderer {
    private static final Logger LOGGER = LogManager.getLogger("Valkyrien Skies Fluid Occlusion");
    private static final int DIRECT_MASKS = 9;
    private static final int BASE_TEXTURE_UNIT = 2;
    private static final int MASK_TEXTURE_WIDTH = FluidOcclusionMaskData.TEXTURE_WIDTH;

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
    private static final FluidOcclusionOverflow OVERFLOW = new FluidOcclusionOverflow();
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

        final int directMasks = handles.maskLocation[0] >= 0 ? DIRECT_MASKS : 0;
        int slot = 0;
        final List<FluidOcclusionOverflow.Entry> overflow = new ArrayList<>();
        for (final ClientShip ship : ships) {
            final var pair = shipWorld.getFluidGameplaySnapshot(ship.getId());
            final FluidTopologyClientCache.CachedSnapshot topology =
                FluidTopologyClientCache.get(shipWorld, ship.getId(), pair);
            if (topology == null) continue;
            final FloodedFluidClientCache.CachedSnapshot flooding =
                FloodedFluidClientCache.get(shipWorld, ship.getId(), pair);
            final ShipMask mask = SHIP_MASKS.computeIfAbsent(ship.getId(), ShipMask::new);
            final boolean direct = slot < directMasks;
            if (!mask.prepare(topology, flooding, direct)) continue;
            if (direct) uploadShipSlot(handles, slot++, ship, mask, cameraX, cameraY, cameraZ);
            else overflow.add(new FluidOcclusionOverflow.Entry(ship, mask.data));
        }
        for (int i = slot; i < directMasks; i++) {
            disableShipSlot(handles, i);
        }

        // Stable packing avoids rewriting the atlas when distance order changes within the overflow set.
        overflow.sort(Comparator.comparingLong(entry -> entry.ship().getId()));
        OVERFLOW.update(overflow, cameraX, cameraY, cameraZ, handles.maskUnit, handles.descriptorUnit);
        OVERFLOW.bind(handles.overflowMaskLocation, handles.overflowDescriptorLocation,
            handles.overflowCountLocation, overflow.size(), handles.maskUnit, handles.descriptorUnit);
        uploadFluidSpriteBounds(handles);
        GL20.glUniform1f(handles.enabledLocation, slot > 0 || !overflow.isEmpty() ? 1.0F : 0.0F);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    public static void disableForProgram(final int programId) {
        final ProgramHandles handles = handles(programId);
        if (handles.enabledLocation >= 0) {
            OVERFLOW.bind(handles.overflowMaskLocation, handles.overflowDescriptorLocation,
                handles.overflowCountLocation, 0, handles.maskUnit, handles.descriptorUnit);
            GL20.glUniform1f(handles.enabledLocation, 0.0F);
        }
    }

    public static void clear() {
        clearMasks();
        PROGRAMS.clear();
        lastLevel = null;
        FluidOcclusionRenderContext.clear();
    }

    /** Called before ShaderInstance deletes its GL program, whose integer ID may be reused. */
    public static void onProgramClosed(final int programId) {
        final ProgramHandles removed = PROGRAMS.remove(programId);
        if (removed != null && removed.enabledLocation >= 0) {
            clearMasks();
            lastLevel = null;
            FluidOcclusionRenderContext.clear();
        }
    }

    private static void clearMasks() {
        OVERFLOW.close();
        for (final ShipMask mask : SHIP_MASKS.values()) {
            mask.close();
        }
        SHIP_MASKS.clear();
        LOADED_SHIP_IDS.clear();
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
        GL20.glUniform1i(handles.partialCountLocation[slot], mask.partialCount);

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
        GL20.glUniform1i(handles.partialCountLocation[slot], 0);
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
        private Object topologyContent;
        private Object floodingContent;
        private int minX;
        private int minY;
        private int minZ;
        private int sizeX;
        private int sizeY;
        private int sizeZ;
        private int partialCount;
        private int textureId;
        private int textureHeight;
        private boolean warnedOversized;
        private FluidOcclusionMaskData data;
        private FluidOcclusionMaskData uploadedData;

        private ShipMask(final long shipId) {
            this.shipId = shipId;
        }

        private boolean update(
            final FluidTopologyClientCache.CachedSnapshot topology,
            final FloodedFluidClientCache.CachedSnapshot flooding
        ) {
            return prepare(topology, flooding, true);
        }

        private boolean prepare(
            final FluidTopologyClientCache.CachedSnapshot topology,
            final FloodedFluidClientCache.CachedSnapshot flooding,
            final boolean individualTexture
        ) {
            final Object newTopologyContent = topology.occlusionIdentity();
            final Object newFloodingContent = flooding == null ? null : flooding.occlusionIdentity();
            if (newTopologyContent != topologyContent || newFloodingContent != floodingContent) {
                try {
                    final FluidOcclusionMaskData rebuilt = FluidOcclusionMaskData.build(topology, flooding);
                    // A fill change outside the domain can leave the final mask unchanged.
                    data = rebuilt != null && rebuilt.sameContent(data) ? data : rebuilt;
                } catch (final IllegalArgumentException oversized) {
                    data = null;
                    if (!warnedOversized) {
                        warnedOversized = true;
                        LOGGER.warn("Skipping fluid-occlusion mask for ship {}: {}", shipId, oversized.getMessage());
                    }
                }
                topologyContent = newTopologyContent;
                floodingContent = newFloodingContent;
            }
            if (data == null) {
                close();
                return false;
            }
            minX = data.minX; minY = data.minY; minZ = data.minZ;
            sizeX = data.sizeX; sizeY = data.sizeY; sizeZ = data.sizeZ;
            partialCount = data.partialCount;
            if (individualTexture) {
                ensureTexture(data.textureHeight);
                if (uploadedData != data) {
                    uploadTexture(textureId, MASK_TEXTURE_WIDTH, data.textureHeight, data.bytes);
                    uploadedData = data;
                }
            } else {
                // Overflow already owns these bytes on the GPU; do not keep a second per-ship texture.
                close();
            }
            return true;
        }

        private void ensureTexture(final int requiredHeight) {
            if (isLiveTexture(textureId) && textureHeight == requiredHeight) return;
            close();
            textureId = TextureUtil.generateTextureId();
            final int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            final int previousAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
            try {
                // External deletion can leave Minecraft's cached binding at a reused name.
                GlStateManager._bindTexture(0);
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
            uploadedData = null;
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
        private final int maskUnit;
        private final int descriptorUnit;
        private final int enabledLocation;
        private final int overflowMaskLocation;
        private final int overflowDescriptorLocation;
        private final int overflowCountLocation;
        private final int[] fluidBoundsLocation = new int[5];
        private final int[] maskLocation = new int[DIRECT_MASKS];
        private final int[] gridSizeLocation = new int[DIRECT_MASKS];
        private final int[] partialCountLocation = new int[DIRECT_MASKS];
        private final int[] cameraLocalLocation = new int[DIRECT_MASKS];
        private final int[] worldToShipLocation = new int[DIRECT_MASKS];

        private ProgramHandles(final int programId) {
            enabledLocation = GL20.glGetUniformLocation(programId, "VsFluidOcclusionEnabled");
            overflowMaskLocation = GL20.glGetUniformLocation(programId, "VsFluidOverflowMasks");
            overflowDescriptorLocation = GL20.glGetUniformLocation(programId, "VsFluidOverflowDescriptors");
            overflowCountLocation = GL20.glGetUniformLocation(programId, "VsFluidOverflowCount");
            for (int i = 0; i < fluidBoundsLocation.length; i++) {
                fluidBoundsLocation[i] =
                    GL20.glGetUniformLocation(programId, "VsFluidOcclusionUv" + i);
            }
            for (int i = 0; i < DIRECT_MASKS; i++) {
                maskLocation[i] =
                    GL20.glGetUniformLocation(programId, "VsFluidOcclusionMask" + i);
                gridSizeLocation[i] =
                    GL20.glGetUniformLocation(programId, "VsFluidOcclusionGridSize" + i);
                partialCountLocation[i] =
                    GL20.glGetUniformLocation(programId, "VsFluidOcclusionPartialCount" + i);
                cameraLocalLocation[i] =
                    GL20.glGetUniformLocation(programId, "VsFluidOcclusionCameraLocal" + i);
                worldToShipLocation[i] =
                    GL20.glGetUniformLocation(programId, "VsFluidOcclusionWorldToShip" + i);
            }
            final boolean compact = maskLocation[0] < 0;
            maskUnit = compact ? FluidOcclusionTextureState.MASK_UNIT : FluidOcclusionOverflow.MASK_UNIT;
            descriptorUnit = compact ? FluidOcclusionTextureState.DESCRIPTOR_UNIT : FluidOcclusionOverflow.DESCRIPTOR_UNIT;
        }
    }
}
