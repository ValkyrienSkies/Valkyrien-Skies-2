package org.valkyrienskies.mod.common.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valkyrienskies.mod.mixin.accessors.client.render.vdex.CompositeRenderTypeAccessor;
import org.valkyrienskies.mod.mixin.accessors.client.render.vdex.CompositeStateAccessor;
import org.valkyrienskies.mod.mixin.accessors.client.render.vdex.RenderStateShardAccessor;
import org.valkyrienskies.mod.mixin.accessors.client.render.vdex.TextureAtlasAccessor;
import org.valkyrienskies.mod.mixin.accessors.client.render.vdex.TextureStateShardAccessor;

/**
 * As of the format-v2 wire change (see BakedGeometrySerializer), every BakedGeometry produced here carries a flat,
 * independent triangle list: vertices.size() is always a multiple of 3, and every consecutive group of 3 is one
 * triangle with no vertex sharing across triangles. Nothing downstream (the serializer, the Python renderer) has to
 * know or guess the source draw call's primitive mode (quad, strip, fan, ...) — that's resolved once, here, via
 * expandToTriangles()/triangleIndices(), and never leaves this class.
 */
public class Bakery {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bakery.class);
    /**
     * Decodes a BakedQuad's raw int[] vertex data. Block-render quads use DefaultVertexFormat.BLOCK: per vertex that's
     * 8 ints — [pos.x, pos.y, pos.z, color, uv.u, uv.v, packedLight, packedNormal].
     */
    public static BakedGeometry bakeQuad(BakedQuad quad, @Nullable Direction cullFace, BlockState bs, BlockPos pos) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        int[] raw = quad.getVertices();
        int intsPerVertex = DefaultVertexFormat.BLOCK.getVertexSize() / 4;

        if (raw.length != intsPerVertex * 4) {
            throw new IllegalStateException(
                "Bread Factory: Unexpected quad vertex data length " + raw.length + ", expected " +
                    (intsPerVertex * 4) + " (non-BLOCK vertex format?)");
        }

        RenderType renderType = ItemBlockRenderTypes.getChunkRenderType(bs);

        // Decode atlas-space UVs for all 4 vertices up front so we can validate/resolve the real
        // sprite before doing any per-vertex UV math.
        float[] atlasU = new float[4];
        float[] atlasV = new float[4];
        for (int vert = 0; vert < 4; vert++) {
            int base = vert * intsPerVertex;
            atlasU[vert] = Float.intBitsToFloat(raw[base + 4]);
            atlasV[vert] = Float.intBitsToFloat(raw[base + 5]);
        }

        // CTM (and other "connected texture" mods) sometimes hand back a getSprite() whose declared
        // bounds don't actually contain this quad's real atlas UVs -- CTM picks a specific sub-icon
        // of a larger connected-texture sheet per quad, and the icon reference it returns doesn't
        // always match the one the raw UVs were baked against. Naive (u-u0)/(u1-u0) division against
        // the wrong sprite then produces wildly out-of-range UVs (we've seen values like -39). Detect
        // that and fall back to scanning the atlas for the sprite that actually contains these UVs.
        TextureAtlasSprite declaredSprite = quad.getSprite();
        TextureAtlasSprite sprite = declaredSprite;
        if (declaredSprite == null || !allContained(declaredSprite, atlasU, atlasV)) {
            TextureAtlasSprite resolved = findSpriteFromRenderType(renderType, atlasU, atlasV);
            if (resolved != null) {
                sprite = resolved;
            } else if (declaredSprite == null) {
                throw new IllegalStateException(
                    "Bread Factory: quad has no sprite and none could be resolved from its UVs");
            }
            // else: no atlas match found either -- fall through and use declaredSprite as a last
            // resort, same as before this fix, rather than crashing on a quad we can't fully trust.
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("bakeQuad: sprite {} did not contain quad UVs, resolved {} instead",
                    declaredSprite == null ? "null" : declaredSprite.contents().name(),
                    sprite == null ? "null" : sprite.contents().name());
            }
        }

        int tintR = 255, tintG = 255, tintB = 255;
        if (quad.isTinted()) {
            Minecraft minecraft = Minecraft.getInstance();
            int tint = minecraft.getBlockColors().getColor(bs, minecraft.level, pos, quad.getTintIndex());
            tintR = (tint >> 16) & 0xFF;
            tintG = (tint >> 8) & 0xFF;
            tintB = tint & 0xFF;
        }

        for (int vert = 0; vert < 4; vert++) {
            int base = vert * intsPerVertex;

            float x = Float.intBitsToFloat(raw[base]);
            float y = Float.intBitsToFloat(raw[base + 1]);
            float z = Float.intBitsToFloat(raw[base + 2]);

            int packedColor = raw[base + 3];
            int r = packedColor & 0xFF;
            int g = (packedColor >> 8) & 0xFF;
            int b = (packedColor >> 16) & 0xFF;
            int a = (packedColor >> 24) & 0xFF;

            if (quad.isTinted()) {
                r = (r * tintR) / 255;
                g = (g * tintG) / 255;
                b = (b * tintB) / 255;
            }

            // Sprite-local UVs, using the validated/resolved sprite rather than trusting getSprite() blindly.
            float u = (atlasU[vert] - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
            float v = (atlasV[vert] - sprite.getV0()) / (sprite.getV1() - sprite.getV0());

            int packedLight = raw[base + 6];
            int lightU = packedLight & 0xFFFF;
            int lightV = (packedLight >>> 16) & 0xFFFF;

            int packedNormal = raw[base + 7];
            float nx = ((byte) (packedNormal & 0xFF)) / 127f;
            float ny = ((byte) ((packedNormal >> 8) & 0xFF)) / 127f;
            float nz = ((byte) ((packedNormal >> 16) & 0xFF)) / 127f;

            consumer.vertex(x, y, z);
            consumer.color(r, g, b, a);
            consumer.uv(u, v);
            consumer.overlayCoords(0, 10);
            consumer.uv2(lightU, lightV);
            consumer.normal(nx, ny, nz);
            consumer.endVertex();
        }

        if (consumer.vertices.size() != 4) {
            throw new IllegalStateException("Bread Factory: expected 4 vertices, got " + consumer.vertices.size());
        }

        List<VertexData> triangles = expandToTriangles(consumer.vertices, VertexFormat.Mode.QUADS);
        return new BakedGeometry(((RenderStateShardAccessor) renderType).getName(), cullFace,
            sprite.contents().name(), triangles);
    }

    public static List<BakedGeometry> bakeQuads(BlockState bs, BlockPos pos) {
        if (Minecraft.getInstance().level == null) {
            throw new IllegalStateException("Bread Factory: cannot bake quads without a valid Level");
        }
        List<BakedGeometry> baked = new ArrayList<>();

        if (bs.hasBlockEntity()) {
            baked.addAll(bakeBlockEntity(Minecraft.getInstance().level.getBlockEntity(pos), Minecraft.getInstance().getFrameTime(), LightTexture.FULL_BRIGHT));
        }
        if (bs.getRenderShape() == RenderShape.MODEL) {
            baked.addAll(PlatformBakery.INSTANCE.bakeBlockQuads(bs, pos));
        }

        return baked;
    }

    /**
     * Bakes a BlockEntity by invoking its BlockEntityRenderer against a recording buffer instead of the real one.
     * Requires `be` to have a valid Level attached — most renderers read be.getLevel() for animation/state and will NPE
     * without one.
     *
     * @param packedLight pass LightTexture.FULL_BRIGHT for a lighting-independent snapshot, or a real packed light
     *                    value to bake real lighting in for this spot.
     */
    private static List<BakedGeometry> bakeBlockEntity(BlockEntity be, float partialTick, int packedLight) {
        BlockEntityRenderer<BlockEntity> renderer =
            Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(be);
        ArrayList<BakedGeometry> baked = new ArrayList<>();
        if (renderer == null) {
            return baked;
        }

        PoseStack poseStack = new PoseStack();
        CapturingBufferSource bufferSource = new CapturingBufferSource();

        renderer.render(be, partialTick, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);

        for (var entry : bufferSource.consumers.entrySet()) {
            RenderType renderType = entry.getKey();
            String renderTypeName = ((RenderStateShardAccessor) renderType).getName();
            baked.addAll(splitByTexture(renderType, renderTypeName, entry.getValue().vertices));
        }
        return baked;
    }

    /**
     * A single RecordingVertexConsumer buffer holds every primitive a block-entity renderer drew under one RenderType —
     * and one RenderType can legitimately carry several distinct sprites (a block entity that draws its own block
     * texture plus a held item's texture, several differently-skinned parts, etc). This decodes the buffer's actual
     * primitives via RenderType.mode() (QUADS/TRIANGLES/TRIANGLE_STRIP/TRIANGLE_FAN — no stride-guessing), then
     * resolves and un-bakes each *triangle's* own sprite independently, coalescing consecutive same-texture triangles
     * back into one BakedGeometry so a single-texture block entity still produces one geometry entry.
     * <p>
     * RenderTypes using a non-triangle mode (LINES, LINE_STRIP, DEBUG_LINES, DEBUG_LINE_STRIP — e.g. debug/hitbox-style
     * drawing some renderers do) have no triangulated textured surface to bake and are skipped entirely; they carry no
     * meaningful UV/texture semantics for this format.
     */
    private static List<BakedGeometry> splitByTexture(RenderType renderType, String renderTypeName,
        List<VertexData> allVertices) {
        List<BakedGeometry> out = new ArrayList<>();
        if (allVertices.isEmpty()) {
            return out;
        }

        List<int[]> triangles = triangleIndices(renderType.mode(), allVertices.size());
        if (triangles.isEmpty()) {
            // Non-triangle primitive mode — nothing textured to bake for this buffer.
            return out;
        }

        ResourceLocation currentTex = null;
        boolean haveCurrent = false;
        List<VertexData> currentGroup = new ArrayList<>();

        for (int[] tri : triangles) {
            VertexData v0 = allVertices.get(tri[0]);
            VertexData v1 = allVertices.get(tri[1]);
            VertexData v2 = allVertices.get(tri[2]);

            // Resolve the sprite from ALL THREE vertices, not just v2. On a stitched atlas,
            // adjacent sprites share an edge, so a vertex lying exactly on that edge satisfies
            // the (inclusive) bounds test for BOTH neighbors. Picking from v2 alone let HashMap
            // iteration order return the wrong neighbor for seam-touching triangles, and
            // unbakeUv() then projected the triangle's other vertices (which sit in the right
            // sprite) outside [0,1] -- producing the wild UVs that downstream rendered as black
            // or mismatched faces. Passing every vertex lets findSpriteByUv pick the one sprite
            // whose rectangle contains the whole triangle, which is unique for any non-degenerate
            // triangle since atlas sprites tile without overlap.
            TextureAtlasSprite sprite = findSpriteFromRenderType(renderType,
                new float[] {v0.u(), v1.u(), v2.u()}, new float[] {v0.v(), v1.v(), v2.v()});

            ResourceLocation tex = null;
            VertexData o0 = v0, o1 = v1, o2 = v2;
            if (sprite != null) {
                tex = sprite.contents().name();
                o0 = unbakeUv(v0, sprite);
                o1 = unbakeUv(v1, sprite);
                o2 = unbakeUv(v2, sprite);
            }
            // sprite == null (non-atlas texture, or unresolved): carried through with raw UVs
            // unchanged, same as the old fallback for an unresolved buffer.

            if (haveCurrent && !Objects.equals(tex, currentTex)) {
                out.add(new BakedGeometry(renderTypeName, null, currentTex, currentGroup));
                currentGroup = new ArrayList<>();
            }
            currentTex = tex;
            haveCurrent = true;
            currentGroup.add(o0);
            currentGroup.add(o1);
            currentGroup.add(o2);
        }

        out.add(new BakedGeometry(renderTypeName, null, currentTex, currentGroup));
        return out;
    }

    private static VertexData unbakeUv(VertexData vertex, TextureAtlasSprite sprite) {
        return new VertexData(vertex.x(), vertex.y(), vertex.z(), vertex.r(), vertex.g(), vertex.b(), vertex.a(),
            (vertex.u() - sprite.getU0()) / (sprite.getU1() - sprite.getU0()),
            (vertex.v() - sprite.getV0()) / (sprite.getV1() - sprite.getV0()), vertex.overlayU(), vertex.overlayV(),
            vertex.lightmapU(), vertex.lightmapV(), vertex.normalX(), vertex.normalY(), vertex.normalZ());
    }

    /**
     * Returns each drawn primitive's 3 vertex indices (into a flat vertex list of the given mode/count) as independent
     * triangles. This is what lets the wire format stop caring about the source draw call's primitive mode entirely —
     * after this, every BakedGeometry.vertices list is a flat triangle list (length a multiple of 3), never an implicit
     * quad/fan/strip that a reader has to know how to unpack.
     * <p>
     * QUADS and TRIANGLES have fixed, non-overlapping strides. TRIANGLE_STRIP and TRIANGLE_FAN share vertices between
     * consecutive primitives, so their indices are computed accordingly — strip winding is alternated every other
     * triangle to preserve front-face orientation. Line-based modes (LINES, LINE_STRIP, DEBUG_LINES, DEBUG_LINE_STRIP)
     * have no triangulated surface at all; an empty list signals "nothing to bake" for that primitive mode.
     */
    private static List<int[]> triangleIndices(VertexFormat.Mode mode, int vertexCount) {
        List<int[]> tris = new ArrayList<>();
        switch (mode) {
            case QUADS -> {
                for (int base = 0; base + 4 <= vertexCount; base += 4) {
                    tris.add(new int[] {base, base + 1, base + 2});
                    tris.add(new int[] {base, base + 2, base + 3});
                }
            }
            case TRIANGLES -> {
                for (int base = 0; base + 3 <= vertexCount; base += 3) {
                    tris.add(new int[] {base, base + 1, base + 2});
                }
            }
            case TRIANGLE_STRIP -> {
                for (int i = 0; i + 2 < vertexCount; i++) {
                    tris.add(i % 2 == 0 ? new int[] {i, i + 1, i + 2} : new int[] {i + 1, i, i + 2});
                }
            }
            case TRIANGLE_FAN -> {
                for (int i = 1; i + 1 < vertexCount; i++) {
                    tris.add(new int[] {0, i, i + 1});
                }
            }
            default -> {
                // LINES, LINE_STRIP, DEBUG_LINES, DEBUG_LINE_STRIP — no textured surface here.
            }
        }
        return tris;
    }

    private static List<VertexData> expandToTriangles(List<VertexData> vertices, VertexFormat.Mode mode) {
        List<VertexData> out = new ArrayList<>(vertices.size());
        for (int[] tri : triangleIndices(mode, vertices.size())) {
            out.add(vertices.get(tri[0]));
            out.add(vertices.get(tri[1]));
            out.add(vertices.get(tri[2]));
        }
        return out;
    }

    private static @Nullable TextureAtlasSprite findSpriteFromRenderType(RenderType renderType, float[] us, float[] vs) {
        if (!(renderType instanceof RenderType.CompositeRenderType)) {
            LOGGER.warn("findSprite: renderType {} is not CompositeRenderType", renderType);
            return null;
        }

        RenderType.CompositeState compositeState = ((CompositeRenderTypeAccessor) renderType).getState();
        RenderStateShard.EmptyTextureStateShard textureStateShard =
            ((CompositeStateAccessor) (Object) compositeState).getTextureState();

        if (!(textureStateShard instanceof RenderStateShard.TextureStateShard textureState)) {
            LOGGER.warn("findSprite: textureStateShard {} is not TextureStateShard", textureStateShard);
            return null;
        }

        ResourceLocation textureLocation = ((TextureStateShardAccessor) textureState).getTexture().orElse(null);
        if (textureLocation == null) {
            LOGGER.warn("findSprite: no texture location on textureState");
            return null;
        }
        if (!isAtlasTexture(textureLocation)) {
            LOGGER.warn("findSprite: texture {} is not an atlas texture", textureLocation);
            return null;
        }

        return findSpriteByUv(textureLocation, us, vs);
    }

    private static boolean isAtlasTexture(ResourceLocation location) {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        return textureManager.getTexture(location) instanceof TextureAtlas;
    }

    /**
     * Scans the atlas for the sprite whose rectangle contains the most of the given UV points.
     * Returns immediately on a sprite that contains all of them (the common, unambiguous case);
     * otherwise falls back to a best-match vote, same idea as before but generalized to N points
     * instead of a hardcoded 3 -- bakeQuad needs 4 (one per quad vertex), splitByTexture needs 3
     * (one per triangle vertex).
     */
    private static @Nullable TextureAtlasSprite findSpriteByUv(ResourceLocation atlasLocation, float[] us, float[] vs) {
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(atlasLocation);

        TextureAtlasSprite bestSprite = null;
        int bestCount = 0;

        for (TextureAtlasSprite sprite : ((TextureAtlasAccessor) atlas).getTexturesByName().values()) {
            int count = 0;
            for (int i = 0; i < us.length; i++) {
                if (contains(sprite, us[i], vs[i])) count++;
            }
            if (count == us.length) {
                return sprite;
            }
            if (count > bestCount) {
                bestCount = count;
                bestSprite = sprite;
            }
        }
        return bestSprite;
    }

    private static boolean allContained(TextureAtlasSprite sprite, float[] us, float[] vs) {
        for (int i = 0; i < us.length; i++) {
            if (!contains(sprite, us[i], vs[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(TextureAtlasSprite sprite, float u, float v) {
        return u >= sprite.getU0() && u <= sprite.getU1() && v >= sprite.getV0() && v <= sprite.getV1();
    }

    /**
     * A simple record to hold vertex data for baking.
     */
    public record VertexData(double x, double y, double z, int r, int g, int b, int a, float u, float v, short overlayU,
                             short overlayV, short lightmapU, short lightmapV, float normalX, float normalY,
                             float normalZ) {
    }

    /**
     * Unified result for both block-model quads and block-entity geometry. `cullFace` is only meaningful for block
     * quads (always null for block entities). `textureResLoc` is only reliably populated for block quads (from the
     * sprite) — for block entities it's a best-effort per-triangle atlas lookup and may be null; see splitByTexture().
     * `vertices` is always a flat, independent triangle list: length a multiple of 3, no vertex sharing between
     * triangles (format v2 invariant — see BakedGeometrySerializer). If you plan to re-render through `renderType`
     * itself rather than a separate texture pipeline, you don't need `textureResLoc` at all.
     */
    public record BakedGeometry(String renderTypeName, @Nullable Direction cullFace,
                                @Nullable ResourceLocation textureResLoc, List<VertexData> vertices) {
    }

    /**
     * Single VertexConsumer implementation shared by both bake paths. Accumulates into a List rather than a fixed-size
     * array — block quads always produce exactly 4 and you can assert that at the call site, but block-entity renderers
     * can emit any primitive count/mode, so the consumer itself must not assume a shape.
     */
    private static class RecordingVertexConsumer implements VertexConsumer {
        final List<VertexData> vertices = new ArrayList<>();

        private double x, y, z;
        private int r, g, b, a;
        private float u, v;
        private short overlayU, overlayV;
        private short lightmapU, lightmapV;
        private float normalX, normalY, normalZ;

        private boolean isUsingDefaultColor = false;
        private boolean overrideDefaultColor = false;
        private int defaultR, defaultG, defaultB, defaultA;

        @Override
        public @NonNull VertexConsumer vertex(double d, double e, double f) {
            this.x = d;
            this.y = e;
            this.z = f;
            return this;
        }

        @Override
        public @NonNull VertexConsumer color(int i, int j, int k, int l) {
            this.overrideDefaultColor = true;
            this.r = i;
            this.g = j;
            this.b = k;
            this.a = l;
            return this;
        }

        @Override
        public @NonNull VertexConsumer uv(float f, float g) {
            this.u = f;
            this.v = g;
            return this;
        }

        @Override
        public @NonNull VertexConsumer overlayCoords(int i, int j) {
            this.overlayU = (short) i;
            this.overlayV = (short) j;
            return this;
        }

        @Override
        public @NonNull VertexConsumer uv2(int i, int j) {
            this.lightmapU = (short) i;
            this.lightmapV = (short) j;
            return this;
        }

        @Override
        public @NonNull VertexConsumer normal(float f, float g, float h) {
            this.normalX = f;
            this.normalY = g;
            this.normalZ = h;
            return this;
        }

        @Override
        public void endVertex() {
            if (isUsingDefaultColor && !overrideDefaultColor) {
                this.r = defaultR;
                this.g = defaultG;
                this.b = defaultB;
                this.a = defaultA;
            }
            vertices.add(
                new VertexData(x, y, z, r, g, b, a, u, v, overlayU, overlayV, lightmapU, lightmapV, normalX, normalY,
                    normalZ));
            overrideDefaultColor = false;
        }

        @Override
        public void defaultColor(int i, int j, int k, int l) {
            this.defaultR = i;
            this.defaultG = j;
            this.defaultB = k;
            this.defaultA = l;
            this.isUsingDefaultColor = true;
        }

        @Override
        public void unsetDefaultColor() {
            this.isUsingDefaultColor = false;
        }
    }

    private static class CapturingBufferSource implements MultiBufferSource {
        final Map<RenderType, RecordingVertexConsumer> consumers = new LinkedHashMap<>();

        @Override
        public @NonNull VertexConsumer getBuffer(@NonNull RenderType renderType) {
            return consumers.computeIfAbsent(renderType, rt -> new RecordingVertexConsumer());
        }
    }
}
