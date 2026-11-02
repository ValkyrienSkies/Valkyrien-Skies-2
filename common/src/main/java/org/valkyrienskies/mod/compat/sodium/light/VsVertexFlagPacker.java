package org.valkyrienskies.mod.compat.sodium.light;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import org.valkyrienskies.mod.common.VS2ChunkAllocator;

/**
 * Packs the AO multiplier, the face direction, and the biome-resolver slot into
 * the alpha byte of a sodium/embeddium chunk vertex color. The VS ship shader
 * decodes the layout to:
 * <ul>
 *   <li>recover the world-space surface normal (after multiplying by the ship's
 *       transform matrix) without having to fall back to {@code dFdx}/{@code dFdy}
 *       on the camera-relative position;</li>
 *   <li>apply directional shade only when the source quad opted in
 *       ({@link net.minecraft.client.renderer.block.model.BakedQuad#isShade()});</li>
 *   <li>apply a world-space biome tint at fragment time.</li>
 * </ul>
 *
 * <p><b>Only applied for shipyard-chunk blocks.</b> World chunks go through
 * sodium's stock shader, which reads {@code v_Color.a} as an 8-bit AO multiplier
 * and {@code v_Color.rgb} as the BlockColors-baked biome tint.
 *
 * <p>Bit layout in the alpha byte (shipyard blocks only):
 * <pre>
 *   bits 0-2: AO level (0=0.0, 1=0.2, 2=0.4, 3=0.6, 4=0.8, 5=1.0, 6/7 unused).
 *             3 bits is enough because sodium's smooth AO produces exactly the
 *             5 fractions of 0.2 — uniform 5/255 quantize to 3 bits is lossless.
 *   bits 3-5: face slot — 0 DOWN, 1 UP, 2 NORTH, 3 SOUTH, 4 WEST, 5 EAST,
 *             6 UNSHADED (emissive faces / fluids), 7 reserved.
 *   bits 6-7: resolverType (0 none, 1 grass, 2 foliage, 3 water).
 * </pre>
 *
 * <p>For biome-dependent quads the RGB component is also forced to white so
 * the shader can multiply in the world-biome color without compounding with
 * the shipyard-biome BlockColors bake.
 */
public final class VsVertexFlagPacker {
    public static final int FACE_DOWN = 0;
    public static final int FACE_UP = 1;
    public static final int FACE_NORTH = 2;
    public static final int FACE_SOUTH = 3;
    public static final int FACE_WEST = 4;
    public static final int FACE_EAST = 5;
    public static final int FACE_UNSHADED = 6;
    /** Emissive / fullbright quad: skip directional shade and AO, force lightmap to max. */
    public static final int FACE_FULLBRIGHT = 7;

    /** Standard MC BLOCK vertex format puts the packed lightmap UV at int offset 6. */
    private static final int VERTEX_LIGHT_OFFSET = 6;

    /**
     * True if the BakedQuad was tagged emissive in its source model JSON
     * ({@code "emissive": true} or a non-zero {@code BakedQuad.getLightEmission()}).
     * Sodium / embeddium store the {@code LightTexture.FULL_BRIGHT} pack into
     * the per-vertex lightmap slot of the BakedQuad's int-packed vertex array
     * for emissive faces; non-emissive faces leave the slot at 0. We probe
     * vertex 0 — emissive flags are quad-level so all four vertices agree.
     */
    public static boolean isEmissiveQuad(BakedQuad quad) {
        int[] verts = quad.getVertices();
        return verts.length > VERTEX_LIGHT_OFFSET && verts[VERTEX_LIGHT_OFFSET] != 0;
    }

    private VsVertexFlagPacker() {}

    public static boolean isShipyardBlock(BlockPos pos) {
        return VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static int resolverTypeFor(BlockState state) {
        return BiomeColorResolvers.forBlock(state.getBlock());
    }

    public static int resolverTypeFor(FluidState state) {
        return state.is(FluidTags.WATER) ? BiomeColorResolvers.WATER : BiomeColorResolvers.NONE;
    }

    /**
     * Pack the per-quad face direction into the 3-bit face slot used in the AO
     * byte. {@code face} may be null for unshaded / non-cardinal quads — those
     * collapse to the UNSHADED sentinel so the shader skips directional shade.
     */
    public static int faceSlot(Direction face, boolean isShaded) {
        if (!isShaded || face == null) return FACE_UNSHADED;
        return switch (face) {
            case DOWN -> FACE_DOWN;
            case UP -> FACE_UP;
            case NORTH -> FACE_NORTH;
            case SOUTH -> FACE_SOUTH;
            case WEST -> FACE_WEST;
            case EAST -> FACE_EAST;
        };
    }

    /**
     * The directional brightness sodium's LightPipeline pre-multiplies into
     * {@code QuadLightData.br[i]} before we see it. Mirrors vanilla
     * {@code Level.getShade(direction, isShaded)} for the standard overworld
     * values — matches every dimension that doesn't override getShade.
     */
    public static float standardShade(Direction face, boolean isShaded) {
        if (!isShaded || face == null) return 1.0f;
        return switch (face) {
            case UP -> 1.0f;
            case DOWN -> 0.5f;
            case NORTH, SOUTH -> 0.8f;
            case EAST, WEST -> 0.6f;
        };
    }

    /**
     * Pack one vertex color. {@code ao} must be the raw AO multiplier with
     * sodium's directional shade already divided out.
     */
    public static int packColor(int origColor, float ao, int resolverType, int faceSlot) {
        int rgb = (resolverType > 0) ? 0x00FFFFFF : (origColor & 0x00FFFFFF);
        // Map [0, 1] → integer level in [0, 5]. Sodium's distinct AO outputs
        // are {0.2, 0.4, 0.6, 0.8, 1.0}, all of which round-trip exactly at this
        // resolution: round(0.2*5)=1, round(1.0*5)=5, etc.
        int aoLevel = Math.max(0, Math.min(5, Math.round(Math.max(0f, Math.min(1f, ao)) * 5f)));
        int packed = (aoLevel & 7) | ((faceSlot & 7) << 3) | ((resolverType & 3) << 6);
        return rgb | (packed << 24);
    }
}
