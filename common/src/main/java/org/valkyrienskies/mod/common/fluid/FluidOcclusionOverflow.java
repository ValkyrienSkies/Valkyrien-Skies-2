package org.valkyrienskies.mod.common.fluid;

import com.mojang.blaze3d.platform.TextureUtil;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.valkyrienskies.core.api.ships.ClientShip;

/** Extra masks share two textures instead of consuming another sampler per ship. OpenGL 3.2 only. */
final class FluidOcclusionOverflow implements AutoCloseable {
    // Terrain samplers use 0/1; reserve 14/15 beyond direct masks and ship-light resources.
    static final int MASK_UNIT = 14;
    static final int DESCRIPTOR_UNIT = 15;
    static final int RECORD_TEXELS = 8;
    private int maskTexture;
    private int descriptorTexture;
    private Layout layout;
    private static final FluidOcclusionMaskData[] EMPTY = new FluidOcclusionMaskData[0];
    private FluidOcclusionMaskData[] uploaded = EMPTY;
    private FloatBuffer descriptors;

    record Entry(ClientShip ship, FluidOcclusionMaskData data) { }

    /** All offsets are exact integers; split them into 16-bit float components in the descriptor. */
    record Layout(int width, int pageHeight, int layers, int descriptorWidth, int descriptorHeight,
                  int[] offsets) {
        static Layout plan(final int[] words, final int maxSize, final int maxLayers) {
            if (words.length == 0 || maxSize < RECORD_TEXELS || maxLayers <= 0) {
                throw new IllegalArgumentException("Invalid fluid atlas dimensions");
            }
            final int width = Integer.highestOneBit(Math.min(FluidOcclusionMaskData.TEXTURE_WIDTH, maxSize));
            final int[] offsets = new int[words.length];
            long total = 0;
            for (int i = 0; i < words.length; i++) {
                if (words[i] <= 0 || words[i] % width != 0) {
                    throw new IllegalArgumentException("Fluid mask rows are not aligned");
                }
                offsets[i] = Math.toIntExact(total);
                total += words[i];
                if (total > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Fluid masks exceed the GPU atlas address space");
                }
            }
            if (total > Integer.MAX_VALUE || total > (long) width * maxSize * maxLayers) {
                throw new IllegalArgumentException("Fluid masks exceed the GPU atlas address space");
            }
            final long rows = total / width;
            int height = 1;
            while (height < Math.min(rows, Math.min(256, maxSize))) height *= 2;
            height = Math.min(height, maxSize);
            while ((rows + height - 1) / height > maxLayers) {
                height = (int) Math.min((long) height * 2, maxSize);
            }
            final int layers = (int) ((rows + height - 1) / height);
            final int descriptorWidth = Math.min(256, maxSize / RECORD_TEXELS * RECORD_TEXELS);
            final long descriptorHeight = ((long) words.length * RECORD_TEXELS + descriptorWidth - 1) / descriptorWidth;
            if (descriptorHeight > maxSize || (long) descriptorWidth * descriptorHeight * 16 > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Fluid descriptors exceed the GPU atlas address space");
            }
            return new Layout(width, height, layers, descriptorWidth, (int) descriptorHeight, offsets);
        }

        boolean sameStorage(final Layout other) {
            return other != null && width == other.width && pageHeight == other.pageHeight && layers == other.layers;
        }
    }

    void update(final List<Entry> entries, final double cameraX, final double cameraY, final double cameraZ) {
        update(entries, cameraX, cameraY, cameraZ, MASK_UNIT, DESCRIPTOR_UNIT);
    }

    void update(final List<Entry> entries, final double cameraX, final double cameraY, final double cameraZ,
                final int maskUnit, final int descriptorUnit) {
        if (entries.isEmpty()) {
            close();
            return;
        }
        if (!live(maskTexture)) maskTexture = 0;
        if (!live(descriptorTexture)) {
            descriptorTexture = 0;
            descriptors = null;
        }
        final int[] words = new int[entries.size()];
        for (int i = 0; i < words.length; i++) words[i] = entries.get(i).data.bytes.capacity() / 4;
        final Layout next = Layout.plan(words, GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE),
            GL11.glGetInteger(GL30.GL_MAX_ARRAY_TEXTURE_LAYERS));
        boolean changed = !next.sameStorage(layout) || uploaded.length != entries.size() || !live(maskTexture);
        for (int i = 0; !changed && i < uploaded.length; i++) changed = uploaded[i] != entries.get(i).data;
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try (UnpackState ignored = new UnpackState()) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + maskUnit);
            if (!next.sameStorage(layout) || !live(maskTexture)) {
                final int replacement = TextureUtil.generateTextureId();
                GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, replacement);
                parameters(GL30.GL_TEXTURE_2D_ARRAY);
                GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL11.GL_RGBA8,
                    next.width, next.pageHeight, next.layers, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                if (GL11.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D_ARRAY, 0, GL11.GL_TEXTURE_WIDTH) != next.width) {
                    TextureUtil.releaseTextureId(replacement);
                    throw new IllegalStateException("Unable to allocate fluid mask atlas; no masks were truncated");
                }
                release(maskTexture);
                maskTexture = replacement;
            } else {
                GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, maskTexture);
            }
            if (changed) {
                for (int i = 0; i < entries.size(); i++) uploadMask(entries.get(i).data, next, next.offsets[i]);
                uploaded = entries.stream().map(Entry::data).toArray(FluidOcclusionMaskData[]::new);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + descriptorUnit);
            final boolean newDescriptor = !live(descriptorTexture);
            if (newDescriptor) descriptorTexture = TextureUtil.generateTextureId();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, descriptorTexture);
            if (layout == null || layout.descriptorWidth != next.descriptorWidth ||
                layout.descriptorHeight != next.descriptorHeight || descriptors == null || newDescriptor) {
                parameters(GL11.GL_TEXTURE_2D);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, next.descriptorWidth,
                    next.descriptorHeight, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
                if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) != next.descriptorWidth) {
                    release(descriptorTexture);
                    descriptorTexture = 0;
                    descriptors = null;
                    throw new IllegalStateException("Unable to allocate fluid descriptors; no masks were truncated");
                }
                descriptors = BufferUtils.createFloatBuffer(next.descriptorWidth * next.descriptorHeight * 4);
            }
            descriptors.clear();
            for (int i = 0; i < entries.size(); i++) {
                describe(descriptors, entries.get(i), next.offsets[i], cameraX, cameraY, cameraZ);
            }
            // Pad the final row; shader count excludes these slots.
            while (descriptors.hasRemaining()) descriptors.put(0.0F);
            descriptors.flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, next.descriptorWidth,
                next.descriptorHeight, GL11.GL_RGBA, GL11.GL_FLOAT, descriptors);
            layout = next;
        } finally {
            // Raw bindings above deliberately avoid GlStateManager's finite 2D texture-state array.
            GL13.glActiveTexture(active);
        }
    }

    void bind(final int maskLocation, final int descriptorLocation, final int countLocation, final int count) {
        bind(maskLocation, descriptorLocation, countLocation, count, MASK_UNIT, DESCRIPTOR_UNIT);
    }

    void bind(final int maskLocation, final int descriptorLocation, final int countLocation, final int count,
              final int maskUnit, final int descriptorUnit) {
        GL20.glUniform1i(maskLocation, maskUnit);
        GL20.glUniform1i(descriptorLocation, descriptorUnit);
        GL20.glUniform1i(countLocation, count);
        if (count == 0) return;
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + maskUnit);
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, maskTexture);
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + descriptorUnit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, descriptorTexture);
        } finally {
            GL13.glActiveTexture(active);
        }
    }

    private static void uploadMask(final FluidOcclusionMaskData data, final Layout plan, final int offset) {
        final int rowBytes = plan.width * 4;
        int row = offset / plan.width;
        int consumed = 0;
        while (consumed < data.bytes.capacity()) {
            final int y = row % plan.pageHeight;
            final int rows = Math.min(plan.pageHeight - y, (data.bytes.capacity() - consumed) / rowBytes);
            final ByteBuffer part = data.bytes.duplicate();
            part.position(consumed).limit(consumed + rows * rowBytes);
            GL12.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, 0, y, row / plan.pageHeight,
                plan.width, rows, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, part);
            consumed += rows * rowBytes;
            row += rows;
        }
    }

    static void describe(final FloatBuffer out, final Entry entry, final int offset,
                         final double cameraX, final double cameraY, final double cameraZ) {
        final FluidOcclusionMaskData data = entry.data;
        final Matrix4dc toWorld = entry.ship.getRenderTransform().getShipToWorld();
        final Matrix4dc toLocal = entry.ship.getRenderTransform().getWorldToShip();
        final Vector3d point = new Vector3d();
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int corner = 0; corner < 8; corner++) {
            toWorld.transformPosition(data.minX + ((corner & 1) == 0 ? 0.0 : data.sizeX),
                data.minY + ((corner & 2) == 0 ? 0.0 : data.sizeY),
                data.minZ + ((corner & 4) == 0 ? 0.0 : data.sizeZ), point);
            minX = Math.min(minX, point.x - cameraX); maxX = Math.max(maxX, point.x - cameraX);
            minY = Math.min(minY, point.y - cameraY); maxY = Math.max(maxY, point.y - cameraY);
            minZ = Math.min(minZ, point.z - cameraZ); maxZ = Math.max(maxZ, point.z - cameraZ);
        }
        out.put(Math.nextDown((float) minX)).put(Math.nextDown((float) minY)).put(Math.nextDown((float) minZ)).put(offset & 65535);
        out.put(Math.nextUp((float) maxX)).put(Math.nextUp((float) maxY)).put(Math.nextUp((float) maxZ)).put(offset >>> 16);
        out.put(data.sizeX).put(data.sizeY).put(data.sizeZ).put(data.partialCount);
        toLocal.transformPosition(cameraX, cameraY, cameraZ, point);
        out.put((float) (point.x - data.minX)).put((float) (point.y - data.minY)).put((float) (point.z - data.minZ)).put(0);
        out.put((float) toLocal.m00()).put((float) toLocal.m01()).put((float) toLocal.m02()).put(0);
        out.put((float) toLocal.m10()).put((float) toLocal.m11()).put((float) toLocal.m12()).put(0);
        out.put((float) toLocal.m20()).put((float) toLocal.m21()).put((float) toLocal.m22()).put(0);
        out.put(0).put(0).put(0).put(0);
    }

    private static void parameters(final int target) {
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static boolean live(final int texture) { return texture != 0 && GL11.glIsTexture(texture); }
    private static void release(final int texture) { if (live(texture)) TextureUtil.releaseTextureId(texture); }

    @Override
    public void close() {
        release(maskTexture);
        release(descriptorTexture);
        maskTexture = descriptorTexture = 0;
        layout = null;
        uploaded = EMPTY;
        descriptors = null;
    }

    private static final class UnpackState implements AutoCloseable {
        private static final int[] KEYS = {GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH,
            GL11.GL_UNPACK_SKIP_ROWS, GL11.GL_UNPACK_SKIP_PIXELS, GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES};
        private final int[] values = new int[KEYS.length];
        private final int buffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        UnpackState() {
            for (int i = 0; i < KEYS.length; i++) {
                values[i] = GL11.glGetInteger(KEYS[i]);
                GL11.glPixelStorei(KEYS[i], i == 0 ? 1 : 0);
            }
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        }
        @Override public void close() {
            for (int i = 0; i < KEYS.length; i++) GL11.glPixelStorei(KEYS[i], values[i]);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffer);
        }
    }
}
