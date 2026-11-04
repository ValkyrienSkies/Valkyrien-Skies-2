package org.valkyrienskies.mod.compat.flooding.shaders.complementary;

import org.valkyrienskies.mod.common.fluid.FluidOcclusionShader;

public final class ComplementaryVolumetrics {
    private ComplementaryVolumetrics() { }

    public static String fragment(final String name, final String source) {
        if (!name.equals("composite1") || !ComplementaryFluidCompat.selected()) return source;
        final String signature = "vec4 GetVolumetricLight(";
        final int start = source.indexOf(signature);
        if (start < 0) return source;
        final int opening = source.indexOf('{', start);
        int end = opening + 1, depth = 1;
        final String syntax = java.util.regex.Pattern.compile("/\\*.*?\\*/|//[^\\r\\n]*", java.util.regex.Pattern.DOTALL)
            .matcher(source).replaceAll(match -> " ".repeat(match.group().length()));
        for (; end < syntax.length() && depth > 0; end++) {
            if (syntax.charAt(end) == '{') depth++;
            else if (syntax.charAt(end) == '}') depth--;
        }
        if (depth != 0) return source;
        final String original = source.substring(start, end);
        final String step = "volumetricLight += stepResult;";
        if (!original.contains(step)) return source;
        final String wet = original.replace("GetVolumetricLight(", "vs_GetWaterVolumetricLight(")
            .replace("isEyeInWater", "vs_WetEye")
            .replaceFirst("\\{", "{\nconst int vs_WetEye = 1;")
            .replace("if (vs_WetEye == 1 && translucentMult == vec3(1.0)) localDensity = vec3(0.0);", "")
            .replace(step, "stepResult *= vs_wetSlice(rayDirection, lastDistance, nextDistance);\n" + step);
        final String air = original.replace(step,
            "if (vs_TraceWet) stepResult *= 1.0 - vs_wetSlice(rayDirection, lastDistance, nextDistance);\n" + step);
        final String call = "volumetricEffect = GetVolumetricLight(vlFactorM, translucentMult, lViewPos, lViewPos1, nViewPos, VdotL, VdotU, z0, z1, z1lod, dither);";
        if (!source.contains(call)) return source;
        final String patched = source.substring(0, start) + wet + "\n" + air + source.substring(end);
        final int header = patched.indexOf('\n') + 1;
        return patched.substring(0, header) + declarations() + patched.substring(header).replace(call,
            "vs_TraceWet = vs_ExteriorFogEnabled && isEyeInWater == 0 && texture2D(vs_ExteriorWaterPath, texCoord).r > 0.001;\n"
            + call + "\nif (vs_TraceWet) {\nfloat vs_WetFactor = 1.0;\n"
            + "vec4 vs_WaterLight = vs_GetWaterVolumetricLight(vs_WetFactor, translucentMult, lViewPos, lViewPos1, nViewPos, VdotL, VdotU, z0, z1, z1lod, dither);\n"
            + "volumetricEffect += vs_WaterLight * vec4(pow2(vec3(0.80, 0.87, 0.97) * 0.55), 1.0);\n}\n");
    }

    private static String declarations() {
        return """
            uniform bool vs_ExteriorFogEnabled;
            uniform sampler2D vs_ExteriorWaterPath;
            uniform sampler3D vs_ExteriorWorldWater;
            uniform vec3 vs_WaterCamera;
            uniform vec3 vs_WaterOffset;
            bool vs_TraceWet = false;
            vec3 valkyrienair_CamRelPos;
            """ + FluidOcclusionShader.atlasDecoder() + """
            float vs_wetAt(vec3 position) {
                vec3 grid = vs_WaterCamera + position;
                ivec3 cell = ivec3(floor(grid));
                ivec3 size = textureSize(vs_ExteriorWorldWater, 0);
                if (any(lessThan(cell, ivec3(0))) || any(greaterThanEqual(cell, size))) return 0.0;
                float height = texelFetch(vs_ExteriorWorldWater, (cell + ivec3(vs_WaterOffset)) % size, 0).r;
                if (height <= fract(grid.y)) return 0.0;
                valkyrienair_CamRelPos = position;
                return VsFluidOcclusionEnabled > 0.5 && vs_shouldCullOverflow(VsFluidOverflowDescriptors) ? 0.0 : 1.0;
            }
            float vs_wetSlice(vec3 direction, float start, float end) {
                float wet = 0.0;
                for (int i = 0; i < 4; i++) wet += vs_wetAt(direction * mix(start, end, (float(i) + 0.5) * 0.25));
                return wet * 0.25;
            }
            """;
    }
}
