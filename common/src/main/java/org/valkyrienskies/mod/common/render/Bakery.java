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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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

    /**
     * Decodes a BakedQuad's raw int[] vertex data. Block-render quads use DefaultVertexFormat.BLOCK: per vertex that's
     * 8 ints — [pos.x, pos.y, pos.z, color, uv.u, uv.v, packedLight, packedNormal].
     */
    private static BakedGeometry bakeQuad(BakedQuad quad, @Nullable Direction cullFace, BlockState bs, BlockPos pos) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        int[] raw = quad.getVertices();
        int intsPerVertex = DefaultVertexFormat.BLOCK.getVertexSize() / 4;

        if (raw.length != intsPerVertex * 4) {
            throw new IllegalStateException(
                "Bread Factory: Unexpected quad vertex data length " + raw.length + ", expected " +
                    (intsPerVertex * 4) + " (non-BLOCK vertex format?)");
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

            float u = Float.intBitsToFloat(raw[base + 4]);
            float v = Float.intBitsToFloat(raw[base + 5]);

            // Convert atlas-space UVs back into sprite-local [0,1] UVs.
            // BakedQuad stores atlas UVs, but the exported format expects sprite-local UVs.
            TextureAtlasSprite sprite = quad.getSprite();
            u = (u - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
            v = (v - sprite.getV0()) / (sprite.getV1() - sprite.getV0());

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
            consumer.overlayCoords(0, 10); // BLOCK format carries no overlay data
            consumer.uv2(lightU, lightV);
            consumer.normal(nx, ny, nz);
            consumer.endVertex();
        }

        if (consumer.vertices.size() != 4) {
            throw new IllegalStateException("Bread Factory: expected 4 vertices, got " + consumer.vertices.size());
        }

        RenderType renderType = ItemBlockRenderTypes.getChunkRenderType(bs);
        // A BakedQuad is always exactly one QUADS-mode primitive — expand it to 2 independent
        // triangles so this geometry's vertex list matches every other geometry's invariant
        // (flat triangle list) rather than being a special 4-vertex case.
        List<VertexData> triangles = expandToTriangles(consumer.vertices, VertexFormat.Mode.QUADS);
        return new BakedGeometry(((RenderStateShardAccessor) renderType).getName(), cullFace,
            quad.getSprite().contents().name(), triangles);
    }

    public static List<BakedGeometry> bakeQuads(BlockState bs, BlockPos pos) {
        if (Minecraft.getInstance().level == null) {
            throw new IllegalStateException("Bread Factory: cannot bake quads without a valid Level");
        }
        List<BakedGeometry> baked = new ArrayList<>();

        if (bs.hasBlockEntity()) {
            baked.addAll(bakeBlockEntity(Minecraft.getInstance().level.getBlockEntity(pos), Minecraft.getInstance().getFrameTime(), LightTexture.FULL_BRIGHT));
        }
        //todo: figure out how to skip cogs, large cogs and shafts
        if (bs.getRenderShape() == RenderShape.MODEL) {
            baked.addAll(bakeBlockQuads(bs, pos));
        }

        return baked;
    }

    private static List<BakedGeometry> bakeBlockQuads(BlockState bs, BlockPos pos) {
        var random = Minecraft.getInstance().level.random;
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(bs);

        List<BakedGeometry> baked = new ArrayList<>();

        // Cull-face-specific quads: each of the 6 directions is baked separately so the
        // resulting BakedGeometry can record which face it belongs to (cullFace), matching
        // how chunk rendering culls per-neighbor.
        for (Direction dir : Direction.values()) {
            for (BakedQuad quad : model.getQuads(bs, dir, random)) {
                baked.add(bakeQuad(quad, dir, bs, pos));
            }
        }

        // Direction-independent quads (cross-shaped plants, etc.) — no cull face applies.
        for (BakedQuad quad : model.getQuads(bs, null, random)) {
            baked.add(bakeQuad(quad, null, bs, pos));
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
            TextureAtlasSprite sprite =
                findSpriteFromRenderType(renderType, v0.u(), v0.v(), v1.u(), v1.v(), v2.u(), v2.v());

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

        if (!currentGroup.isEmpty()) {
            out.add(new BakedGeometry(renderTypeName, null, currentTex, currentGroup));
        }
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

    private static @Nullable TextureAtlasSprite findSpriteFromRenderType(RenderType renderType, float u0, float v0,
        float u1, float v1, float u2, float v2) {
        if (!(renderType instanceof RenderType.CompositeRenderType)) {
            return null;
        }

        RenderType.CompositeState compositeState = ((CompositeRenderTypeAccessor) renderType).getState();

        RenderStateShard.EmptyTextureStateShard textureStateShard =
            ((CompositeStateAccessor) (Object) compositeState).getTextureState();

        if (!(textureStateShard instanceof RenderStateShard.TextureStateShard textureState)) {
            return null;
        }

        ResourceLocation textureLocation = ((TextureStateShardAccessor) textureState).getTexture().orElse(null);

        if (textureLocation == null || !isAtlasTexture(textureLocation)) {
            return null;
        }

        return findSpriteByUv(textureLocation, u0, v0, u1, v1, u2, v2);
    }

    private static boolean isAtlasTexture(ResourceLocation location) {
        return location.getPath().contains("/atlas/");
    }

    private static @Nullable TextureAtlasSprite findSpriteByUv(ResourceLocation atlasLocation, float u0, float v0,
        float u1, float v1, float u2, float v2) {
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(atlasLocation);
        // Stitched atlas sprites tile the atlas without overlap -- two neighbors share only an
        // edge. Because the bounds test below is inclusive on both ends, a vertex lying exactly
        // on a shared edge satisfies it for BOTH neighbors. Resolving from a single vertex then
        // picked whichever neighbor HashMap happened to visit first; for a triangle with one
        // vertex on the seam and the other two inside the *other* sprite, that returned the
        // wrong sprite and unbakeUv() pushed the interior vertices outside [0,1].
        //
        // Fix: return the sprite whose rectangle contains ALL THREE vertices. For any
        // non-degenerate triangle that is unique (sprites don't overlap), and the seam vertex
        // -- valid under either neighbor by the inclusive test -- is disambiguated by the two
        // vertices that sit in only one of them.
        TextureAtlasSprite firstHit = null;
        for (TextureAtlasSprite sprite : ((TextureAtlasAccessor) atlas).getTexturesByName().values()) {
            if (contains(sprite, u0, v0)) {
                if (firstHit == null) {
                    firstHit = sprite;
                }
                if (contains(sprite, u1, v1) && contains(sprite, u2, v2)) {
                    return sprite;
                }
            }
        }
        // No single sprite contains all three (a triangle genuinely straddling a seam, or a
        // vertex that didn't land in any sprite). Fall back to the first sprite containing v0
        // so behavior degrades to the old single-vertex result rather than returning null.
        return firstHit;
    }

    private static boolean contains(TextureAtlasSprite sprite, float u, float v) {
        return u >= sprite.getU0() && u <= sprite.getU1() && v >= sprite.getV0() && v <= sprite.getV1();
    }

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
