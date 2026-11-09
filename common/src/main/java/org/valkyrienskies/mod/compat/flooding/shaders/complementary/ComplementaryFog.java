package org.valkyrienskies.mod.compat.flooding.shaders.complementary;

import org.valkyrienskies.mod.compat.flooding.shaders.NativeFluidCompat;

public final class ComplementaryFog {
    private ComplementaryFog() { }

    public static String fragment(final String name, final String source) {
        if (!name.equals("deferred1") || !NativeFluidCompat.complementarySelected()) return source;
        final String fog = "if (isEyeInWater == 1) DoWaterFog(color, lViewPos);";
        final String color = "pow(fogColor, vec3(0.33, 0.21, 0.26))";
        if (!source.contains(fog) || !source.contains("vec3 waterFogColor")) return source;
        final int header = source.indexOf('\n') + 1;
        return source.substring(0, header)
            + "uniform bool vs_ExteriorFogEnabled;\nuniform sampler2D vs_ExteriorWaterPath;\nuniform vec3 vs_ExteriorWaterColor;\n"
            + source.substring(header).replace(color,
                "pow(vs_ExteriorFogEnabled ? vs_ExteriorWaterColor : fogColor, vec3(0.33, 0.21, 0.26))")
                .replace(fog, "if (vs_ExteriorFogEnabled && !isReflection) DoWaterFog(color, texture2D(vs_ExteriorWaterPath, gl_FragCoord.xy / vec2(viewWidth, viewHeight)).r); else " + fog)
                .replace("gl_FragData[0] = vec4(color.rgb, 1.0);",
                    "if (vs_ExteriorFogEnabled && z0 == 1.0) DoWaterFog(color, texture2D(vs_ExteriorWaterPath, gl_FragCoord.xy / vec2(viewWidth, viewHeight)).r);\ngl_FragData[0] = vec4(color.rgb, 1.0);");
    }
}
