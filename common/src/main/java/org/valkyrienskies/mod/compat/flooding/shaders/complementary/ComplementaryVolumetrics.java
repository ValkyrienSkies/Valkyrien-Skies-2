package org.valkyrienskies.mod.compat.flooding.shaders.complementary;

import org.valkyrienskies.mod.compat.flooding.shaders.NativeFluidCompat;
import org.valkyrienskies.mod.compat.flooding.shaders.ShaderWaterVolume;

public final class ComplementaryVolumetrics {
    private ComplementaryVolumetrics() { }

    public static String fragment(final String name, final String source) {
        if (!name.equals("composite1") || !NativeFluidCompat.complementarySelected()) return source;
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
        return patched.substring(0, header) + ShaderWaterVolume.DECLARATIONS + "bool vs_TraceWet = false;\n" + patched.substring(header).replace(call,
            "vs_TraceWet = vs_ExteriorFogEnabled && isEyeInWater == 0 && texture2D(vs_ExteriorWaterPath, texCoord).r > 0.001;\n"
            + call + "\nif (vs_TraceWet) {\nfloat vs_WetFactor = 1.0;\n"
            + "vec4 vs_WaterLight = vs_GetWaterVolumetricLight(vs_WetFactor, translucentMult, lViewPos, lViewPos1, nViewPos, VdotL, VdotU, z0, z1, z1lod, dither);\n"
            + "volumetricEffect += vs_WaterLight * vec4(pow2(vec3(0.80, 0.87, 0.97) * 0.55), 1.0);\n}\n");
    }
}
