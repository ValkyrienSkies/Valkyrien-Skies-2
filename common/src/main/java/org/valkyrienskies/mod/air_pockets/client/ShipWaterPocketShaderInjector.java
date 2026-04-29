package org.valkyrienskies.mod.air_pockets.client;

import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Injects external-world water culling support into third-party chunk shaders (Embeddium).
 *
 * <p>Vanilla uses an overridden {@code rendertype_translucent} shader via resources. Embeddium uses its own chunk
 * shaders, so we patch the GLSL source at load time.
 */
public final class ShipWaterPocketShaderInjector {

    private ShipWaterPocketShaderInjector() {}

    private static final Logger LOGGER = LogManager.getLogger("ValkyrienAir ShipWaterCull");

    private static final String INJECT_MARKER = "valkyrienair:ship_water_pocket_cull";
    private static final String INJECT_MARKER_VERTEX = "valkyrienair:ship_water_pocket_cull_vertex";
    private static final String VA_PATCH_APPLIED_MARKER = "VA_PATCH_APPLIED";
    // Must match the patched vanilla core shaders and ShipWaterPocketExternalWaterCull.MAX_SHIPS.
    private static final int MAX_SHIP_SLOTS = 9;

    private static final String EMBEDDIUM_VERTEX_OUT_DECL = "\nout vec3 valkyrienair_WorldPos;\n";

    private static final String EMBEDDIUM_VERTEX_MAIN_INJECT = "\n    valkyrienair_WorldPos = position;\n";
    private static final String EMBEDDIUM_VERTEX_MAIN_INJECT_FALLBACK = "\n    valkyrienair_WorldPos = _vert_position + translation;\n";
    private static final String EMBEDDIUM_VERTEX_UNIFORM_CHUNK_WORLD_ORIGIN_DECL = "\nuniform vec3 ValkyrienAir_ChunkWorldOrigin;\n";

    private static final String EMBEDDIUM_FRAGMENT_IN_DECL = "\nin vec3 valkyrienair_WorldPos;\n";

    private static final Pattern GLSL_VERSION_LINE =
        Pattern.compile("(?m)^\\s*#version\\b.*$");
    private static final Pattern GLSL_OUT_ANY_LINE =
        Pattern.compile("(?m)^\\s*out\\s+\\w+\\s+\\w+\\s*;\\s*(?://.*)?$");
    private static final Pattern GLSL_IN_ANY_LINE =
        Pattern.compile("(?m)^\\s*in\\s+\\w+\\s+\\w+\\s*;\\s*(?://.*)?$");
    private static final Pattern GLSL_OUT_FRAGCOLOR_ANY_LINE =
        Pattern.compile("(?m)^\\s*out\\s+vec4\\s+\\w+\\s*;\\s*(?://.*)?$");
    private static final Pattern GLSL_GL_POSITION_LINE =
        Pattern.compile("(?m)^\\s*gl_Position\\s*=.*;\\s*$");
    private static final Pattern GLSL_GL_POSITION_VEC4_ARG =
        Pattern.compile("(?m)^\\s*gl_Position\\s*=.*\\bvec4\\s*\\(\\s*([^,\\)]+?)\\s*,\\s*1\\.0\\s*\\)\\s*;\\s*$");

    private static final Pattern EMBEDDIUM_VSH_OUT_TEXCOORD_LINE =
        Pattern.compile("(?m)^\\s*out\\s+vec2\\s+v_TexCoord\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_VSH_OUT_COLOR_LINE =
        Pattern.compile("(?m)^\\s*out\\s+vec4\\s+v_Color\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_VSH_POSITION_LINE =
        Pattern.compile("(?m)^\\s*vec3\\s+position\\s*=\\s*_vert_position\\s*\\+\\s*translation\\s*;\\s*$");
    private static final Pattern EMBEDDIUM_VSH_POSITION_WORLD_LINE =
        Pattern.compile("(?m)^\\s*vec3\\s+position\\s*=\\s*_vert_position\\s*\\+\\s*translation\\s*;\\s*$");
    private static final Pattern EMBEDDIUM_VSH_TRANSLATION_LINE =
        Pattern.compile("(?m)^\\s*vec3\\s+translation\\s*=\\s*.*;\\s*$");
    private static final Pattern EMBEDDIUM_VSH_TRANSLATION_RHS =
        Pattern.compile("(?m)^\\s*vec3\\s+translation\\s*=\\s*(.*?);\\s*$");
    private static final Pattern EMBEDDIUM_VSH_UNIFORM_REGION_OFFSET =
        Pattern.compile("(?m)^\\s*uniform\\s+\\w+\\s+u_RegionOffset\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_VSH_VERT_INIT_LINE =
        Pattern.compile("(?m)^\\s*_vert_init\\s*\\(\\s*\\)\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_FSH_IN_TEXCOORD_LINE =
        Pattern.compile("(?m)^\\s*in\\s+vec2\\s+v_TexCoord\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_FSH_IN_COLOR_LINE =
        Pattern.compile("(?m)^\\s*in\\s+vec4\\s+v_Color\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_FSH_OUT_FRAGCOLOR_LINE =
        Pattern.compile("(?m)^\\s*out\\s+vec4\\s+fragColor\\s*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_FSH_FRAGCOLOR_ASSIGN_LINE =
        Pattern.compile("(?m)^\\s*fragColor\\s*=.*;\\s*(?://.*)?$");
    private static final Pattern EMBEDDIUM_MAIN_SIGNATURE =
        Pattern.compile("(?s)\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");

	    	    private static final String EMBEDDIUM_FRAGMENT_DECLS = buildEmbeddiumFragmentDecls();

	    private static String buildEmbeddiumFragmentDecls() {
	        final StringBuilder sb = new StringBuilder(16384);
	        sb.append("\n// ").append(INJECT_MARKER).append('\n');
	        sb.append("// ").append(VA_PATCH_APPLIED_MARKER).append('\n');
	        sb.append("uniform float ValkyrienAir_CullEnabled;\n");
	        sb.append("uniform float ValkyrienAir_IsShipPass;\n");
	        sb.append("uniform vec3 ValkyrienAir_CameraWorldPos;\n");
	        sb.append("uniform sampler2D ValkyrienAir_FluidMask;\n\n");
	        sb.append("uniform vec4 ValkyrienAir_WaterStillUv;\n");
	        sb.append("uniform vec4 ValkyrienAir_WaterFlowUv;\n");
	        sb.append("uniform vec4 ValkyrienAir_WaterOverlayUv;\n");
	        sb.append("uniform float ValkyrienAir_ShipWaterTintEnabled;\n");
	        sb.append("uniform vec3 ValkyrienAir_ShipWaterTint;\n\n");

	        for (int i = 0; i < MAX_SHIP_SLOTS; i++) {
	            sb.append("uniform vec4 ValkyrienAir_ShipAabbMin").append(i).append(";\n");
	            sb.append("uniform vec4 ValkyrienAir_ShipAabbMax").append(i).append(";\n");
	            sb.append("uniform vec4 ValkyrienAir_GridSize").append(i).append(";\n");
	            sb.append("uniform mat4 ValkyrienAir_WorldToShip").append(i).append(";\n");
	            sb.append("uniform sampler2D ValkyrienAir_Mask").append(i).append(";\n\n");
	        }

	        sb.append("const int VA_MASK_TEX_WIDTH_SHIFT = 12;\n");
	        sb.append("const int VA_MASK_TEX_WIDTH_MASK = (1 << VA_MASK_TEX_WIDTH_SHIFT) - 1;\n\n");
	        sb.append("const int VA_SUB = 8;\n");
	        sb.append("const int VA_OCC_WORDS_PER_VOXEL = 16;\n");
	        sb.append("const float VA_WORLD_SAMPLE_EPS = 0.0001;\n\n");

	        sb.append("bool va_inUv(vec2 uv, vec4 bounds) {\n");
	        sb.append("    return uv.x >= bounds.x && uv.x <= bounds.z && uv.y >= bounds.y && uv.y <= bounds.w;\n");
	        sb.append("}\n\n");

	        sb.append("bool va_isWaterUv(vec2 uv) {\n");
	        sb.append("    return va_inUv(uv, ValkyrienAir_WaterStillUv) ||\n");
	        sb.append("        va_inUv(uv, ValkyrienAir_WaterFlowUv) ||\n");
	        sb.append("        va_inUv(uv, ValkyrienAir_WaterOverlayUv);\n");
	        sb.append("}\n\n");

	        sb.append("bool va_isFluidUv(vec2 uv) {\n");
	        sb.append("    return texture(ValkyrienAir_FluidMask, uv).r > 0.5;\n");
	        sb.append("}\n\n");

	        sb.append("uint va_fetchWord(sampler2D tex, int wordIndex) {\n");
	        sb.append("    ivec2 coord = ivec2(wordIndex & VA_MASK_TEX_WIDTH_MASK, wordIndex >> VA_MASK_TEX_WIDTH_SHIFT);\n");
	        sb.append("    vec4 raw = texelFetch(tex, coord, 0) * 255.0;\n");
	        sb.append("    uvec4 bytes = uvec4(round(raw));\n");
	        sb.append("    return bytes.r | (bytes.g << 8u) | (bytes.b << 16u) | (bytes.a << 24u);\n");
	        sb.append("}\n\n");

	        // Combined mask texture: occ words first, then air words.
	        sb.append("bool va_testAir(sampler2D mask, int voxelIdx, ivec3 isize) {\n");
	        sb.append("    int volume = isize.x * isize.y * isize.z;\n");
	        sb.append("    int occBase = volume * VA_OCC_WORDS_PER_VOXEL;\n");
	        sb.append("    int wordIndex = occBase + (voxelIdx >> 5);\n");
	        sb.append("    int bit = voxelIdx & 31;\n");
	        sb.append("    uint word = va_fetchWord(mask, wordIndex);\n");
	        sb.append("    return ((word >> uint(bit)) & 1u) != 0u;\n");
	        sb.append("}\n\n");

	        sb.append("bool va_testOcc(sampler2D mask, int voxelIdx, int subIdx) {\n");
	        sb.append("    int wordIndex = voxelIdx * VA_OCC_WORDS_PER_VOXEL + (subIdx >> 5);\n");
	        sb.append("    int bit = subIdx & 31;\n");
	        sb.append("    uint word = va_fetchWord(mask, wordIndex);\n");
	        sb.append("    return ((word >> uint(bit)) & 1u) != 0u;\n");
	        sb.append("}\n\n");

	        for (int i = 0; i < MAX_SHIP_SLOTS; i++) {
	            sb.append("bool va_shouldDiscardForShip").append(i).append("(vec3 worldPos) {\n");
	            sb.append("    if (ValkyrienAir_GridSize").append(i).append(".x <= 0.0) return false;\n");
	            sb.append("    if (worldPos.x < ValkyrienAir_ShipAabbMin").append(i).append(".x || worldPos.x > ValkyrienAir_ShipAabbMax").append(i).append(".x) return false;\n");
	            sb.append("    if (worldPos.y < ValkyrienAir_ShipAabbMin").append(i).append(".y || worldPos.y > ValkyrienAir_ShipAabbMax").append(i).append(".y) return false;\n");
	            sb.append("    if (worldPos.z < ValkyrienAir_ShipAabbMin").append(i).append(".z || worldPos.z > ValkyrienAir_ShipAabbMax").append(i).append(".z) return false;\n\n");

	            sb.append("    vec3 shipPos = (ValkyrienAir_WorldToShip").append(i).append(" * vec4(worldPos, 1.0)).xyz;\n");
	            sb.append("    vec3 localPos = shipPos;\n");
	            sb.append("    vec3 size = ValkyrienAir_GridSize").append(i).append(".xyz;\n");
	            sb.append("    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;\n");
	            sb.append("    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;\n\n");

	            sb.append("    ivec3 v = ivec3(floor(localPos));\n");
	            sb.append("    ivec3 isize = ivec3(size);\n");
	            sb.append("    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);\n\n");

	            sb.append("    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));\n");
	            sb.append("    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));\n");
	            sb.append("    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);\n\n");

	            sb.append("    if (va_testOcc(ValkyrienAir_Mask").append(i).append(", voxelIdx, subIdx)) return true;\n");
	            sb.append("    if (va_testAir(ValkyrienAir_Mask").append(i).append(", voxelIdx, isize)) return true;\n");
	            sb.append("    return false;\n");
	            sb.append("}\n\n");
	        }

	        return sb.toString();
	    }

	    private static String buildEmbeddiumFragmentMainInject() {
	        final StringBuilder sb = new StringBuilder(1024);
	        sb.append("\n    if (ValkyrienAir_CullEnabled > 0.5 && ValkyrienAir_IsShipPass < 0.5 && va_isFluidUv(v_TexCoord)) {\n");
	        sb.append("        // Sample slightly inside the water volume (below the surface) so we test the water block itself.\n");
	        sb.append("        vec3 camRelPos = valkyrienair_WorldPos + vec3(0.0, -VA_WORLD_SAMPLE_EPS, 0.0);\n");
	        sb.append("        vec3 worldPos = camRelPos + ValkyrienAir_CameraWorldPos;\n");
	        sb.append("        if (");
	        for (int i = 0; i < MAX_SHIP_SLOTS; i++) {
	            if (i != 0) sb.append(" || ");
	            sb.append("va_shouldDiscardForShip").append(i).append("(worldPos)");
	        }
	        sb.append(") {\n");
	        sb.append("            discard;\n");
	        sb.append("        }\n");
	        sb.append("    }\n\n");
	        return sb.toString();
	    }


        private static final String EMBEDDIUM_FRAGMENT_TINT_INJECT = """
                if (ValkyrienAir_ShipWaterTintEnabled > 0.5 && va_isWaterUv(v_TexCoord)) {
                    fragColor.rgb *= ValkyrienAir_ShipWaterTint;
                }

            """;

	    	    private static final String EMBEDDIUM_FRAGMENT_MAIN_INJECT = buildEmbeddiumFragmentMainInject();

    private static boolean loggedEmbeddiumVertexPatchFailed = false;
    private static boolean loggedEmbeddiumFragmentPatchFailed = false;

    private static boolean isEmbeddiumBlockLayerShader(final String identifierPath, final String extension) {
        if (identifierPath == null) return false;
        if (!identifierPath.endsWith(extension)) return false;

        // Normalize older "shaders/blocks/..." identifiers to "blocks/...".
        String path = identifierPath;
        final int shadersIdx = path.indexOf("shaders/");
        if (shadersIdx >= 0) {
            path = path.substring(shadersIdx + "shaders/".length());
        }
        // Keep existing block_layer_* coverage, plus a small allow-list for known Embeddium chunk-shader families.
        return path.startsWith("blocks/block_layer_") || path.startsWith("blocks/fluid_layer_");
    }

    public static String injectSodiumShader(final ResourceLocation identifier, final String source) {
        if (source == null) return null;
        if (source.contains(INJECT_MARKER) || source.contains("ValkyrienAir_CullEnabled") || source.contains("valkyrienair_WorldPos")) {
            return source;
        }
        if (identifier == null) return source;

        final String path = identifier.getPath();
        if (path == null) return source;

        // Accept both modern ("blocks/...") and older ("shaders/blocks/...") Embeddium shader identifiers.
        if (isEmbeddiumBlockLayerShader(path, ".vsh")) {
            final String patched = injectEmbeddiumVertexShader(source);
            return patched;
        }
        if (isEmbeddiumBlockLayerShader(path, ".fsh")) {
            final String patched = injectEmbeddiumFragmentShader(source);
            return patched;
        }

        return source;
    }

    private static String injectEmbeddiumVertexShader(final String source) {
        String out = source;

        if (!out.contains(VA_PATCH_APPLIED_MARKER)) {
            out = insertAfterFirstRegexLine(out, GLSL_VERSION_LINE, "\n// " + VA_PATCH_APPLIED_MARKER + "\n");
        }

        // The injection points here must tolerate indentation and line-ending differences across Embeddium builds.
        out = insertAfterFirstRegexLine(out, EMBEDDIUM_VSH_OUT_TEXCOORD_LINE, EMBEDDIUM_VERTEX_OUT_DECL);
        if (!out.contains("out vec3 valkyrienair_WorldPos;")) {
            out = insertAfterFirstRegexLine(out, EMBEDDIUM_VSH_OUT_COLOR_LINE, EMBEDDIUM_VERTEX_OUT_DECL);
        }
        if (!out.contains("out vec3 valkyrienair_WorldPos;")) {
            out = insertAfterFirstRegexLine(out, GLSL_OUT_ANY_LINE, EMBEDDIUM_VERTEX_OUT_DECL);
        }
        if (!out.contains("out vec3 valkyrienair_WorldPos;")) {
            out = insertAfterFirstRegexLine(out, GLSL_VERSION_LINE, "\n// " + INJECT_MARKER_VERTEX + "\n" + EMBEDDIUM_VERTEX_OUT_DECL);
        }

        if (!out.contains("valkyrienair_WorldPos =")) {
            final boolean hasRegionOffsetUniform = EMBEDDIUM_VSH_UNIFORM_REGION_OFFSET.matcher(out).find();
            if (hasRegionOffsetUniform) {
                final String translationRhs = findFirstRegexLineGroup(out, EMBEDDIUM_VSH_TRANSLATION_RHS, 1);
                final boolean translationIncludesRegionOffset =
                    translationRhs != null && translationRhs.contains("u_RegionOffset");

                final String injection;
                if (translationIncludesRegionOffset) {
                    injection = "\n    valkyrienair_WorldPos = _vert_position + translation;\n";
                } else {
                    injection = "\n    valkyrienair_WorldPos = position + vec3(u_RegionOffset);\n";
                }
                out = insertAfterFirstRegexLine(out, EMBEDDIUM_VSH_POSITION_LINE, injection);
            }
        }

        if (!out.contains("valkyrienair_WorldPos =")) {
            // Fallback: use translation if present (may be world or camera-relative depending on shader variant).
            out = insertAfterFirstRegexLine(out, EMBEDDIUM_VSH_TRANSLATION_LINE,
                "\n    valkyrienair_WorldPos = _vert_position + translation;\n");
        }

        if (!out.contains("valkyrienair_WorldPos =")) {
            // Last resort: introduce an explicit world-origin addend we can upload from Java.
            if (!out.contains("uniform vec3 ValkyrienAir_ChunkWorldOrigin;")) {
                out = insertAfterFirstRegexLine(out, GLSL_VERSION_LINE, EMBEDDIUM_VERTEX_UNIFORM_CHUNK_WORLD_ORIGIN_DECL);
            }
            out = insertAfterFirstRegexLine(out, EMBEDDIUM_VSH_VERT_INIT_LINE,
                "\n    valkyrienair_WorldPos = _vert_position + ValkyrienAir_ChunkWorldOrigin;\n");
            if (!out.contains("valkyrienair_WorldPos =")) {
                out = insertAfterFirstRegexLine(out, EMBEDDIUM_MAIN_SIGNATURE,
                    "\n    valkyrienair_WorldPos = _vert_position + ValkyrienAir_ChunkWorldOrigin;\n");
            }
        }

        if (!out.contains("out vec3 valkyrienair_WorldPos;") || !out.contains("valkyrienair_WorldPos =")) {
            if (!loggedEmbeddiumVertexPatchFailed) {
                loggedEmbeddiumVertexPatchFailed = true;
                LOGGER.warn("Failed to fully patch Embeddium vertex shader for ship water culling; WorldPos output may be invalid");
            }
        }

        return out;
    }

    private static String injectEmbeddiumFragmentShader(final String source) {
        String out = source;

        if (!out.contains(VA_PATCH_APPLIED_MARKER)) {
            out = insertAfterFirstRegexLine(out, GLSL_VERSION_LINE, "\n// " + VA_PATCH_APPLIED_MARKER + "\n");
        }

        out = insertAfterFirstRegexLine(out, EMBEDDIUM_FSH_IN_TEXCOORD_LINE, EMBEDDIUM_FRAGMENT_IN_DECL);
        if (!out.contains("in vec3 valkyrienair_WorldPos;")) {
            out = insertAfterFirstRegexLine(out, EMBEDDIUM_FSH_IN_COLOR_LINE, EMBEDDIUM_FRAGMENT_IN_DECL);
        }
        if (!out.contains("in vec3 valkyrienair_WorldPos;")) {
            out = insertAfterFirstRegexLine(out, GLSL_IN_ANY_LINE, EMBEDDIUM_FRAGMENT_IN_DECL);
        }

        // Insert declarations/helpers before main.
        out = insertAfterFirstRegexLine(out, EMBEDDIUM_FSH_OUT_FRAGCOLOR_LINE, "\n" + EMBEDDIUM_FRAGMENT_DECLS + "\n");
        if (!out.contains(INJECT_MARKER)) {
            out = insertAfterFirstRegexLine(out, GLSL_OUT_FRAGCOLOR_ANY_LINE, "\n" + EMBEDDIUM_FRAGMENT_DECLS + "\n");
        }
        if (!out.contains(INJECT_MARKER)) {
            // Last resort: insert just before main().
            out = insertBeforeFirstRegex(out, EMBEDDIUM_MAIN_SIGNATURE, "\n" + EMBEDDIUM_FRAGMENT_DECLS + "\n");
        }

        // Inject cull check at the top of main().
        out = insertAfterFirstRegex(out, EMBEDDIUM_MAIN_SIGNATURE, EMBEDDIUM_FRAGMENT_MAIN_INJECT);

        // Inject water tint after the fragment color is assigned.
        out = insertAfterFirstRegexLine(out, EMBEDDIUM_FSH_FRAGCOLOR_ASSIGN_LINE, EMBEDDIUM_FRAGMENT_TINT_INJECT);

        if (!out.contains(INJECT_MARKER) || !out.contains("in vec3 valkyrienair_WorldPos;")) {
            if (!loggedEmbeddiumFragmentPatchFailed) {
                loggedEmbeddiumFragmentPatchFailed = true;
                LOGGER.warn("Failed to fully patch Embeddium fragment shader for ship water culling; culling may be inactive");
            }
        }

        return out;
    }

    private static String insertAfterFirstLine(final String source, final String lineStart, final String insert) {
        if (source == null || insert == null) return source;
        final int idx = source.indexOf(lineStart);
        if (idx < 0) return source;
        final int end = source.indexOf('\n', idx);
        if (end < 0) return source;
        return source.substring(0, end + 1) + insert + source.substring(end + 1);
    }

    private static String insertAfterFirst(final String source, final String needle, final String insert) {
        if (source == null || needle == null || needle.isEmpty() || insert == null) return source;
        final int idx = source.indexOf(needle);
        if (idx < 0) return source;
        return source.substring(0, idx + needle.length()) + insert + source.substring(idx + needle.length());
    }

    private static String insertAfterFirstRegexLine(final String source, final Pattern linePattern, final String insert) {
        if (source == null || linePattern == null || insert == null) return source;
        final Matcher m = linePattern.matcher(source);
        if (!m.find()) return source;

        int pos = m.end();
        if (pos < source.length()) {
            final char c = source.charAt(pos);
            if (c == '\r') {
                pos++;
                if (pos < source.length() && source.charAt(pos) == '\n') pos++;
            } else if (c == '\n') {
                pos++;
            }
        }

        return source.substring(0, pos) + insert + source.substring(pos);
    }

    private static String insertAfterFirstRegex(final String source, final Pattern pattern, final String insert) {
        if (source == null || pattern == null || insert == null) return source;
        final Matcher m = pattern.matcher(source);
        if (!m.find()) return source;
        final int pos = m.end();
        return source.substring(0, pos) + insert + source.substring(pos);
    }

    private static String insertBeforeFirstRegex(final String source, final Pattern pattern, final String insert) {
        if (source == null || pattern == null || insert == null) return source;
        final Matcher m = pattern.matcher(source);
        if (!m.find()) return source;
        final int pos = m.start();
        return source.substring(0, pos) + insert + source.substring(pos);
    }

    private static String findFirstRegexLineGroup(final String source, final Pattern linePattern, final int group) {
        if (source == null || linePattern == null) return null;
        final Matcher m = linePattern.matcher(source);
        if (!m.find()) return null;
        try {
            return m.group(group);
        } catch (final Exception ignored) {
            return null;
        }
    }
}
