package org.valkyrienskies.mod.compat.flooding.shaders.bsl;

import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.valkyrienskies.mod.compat.flooding.shaders.NativeFluidCompat;
import org.valkyrienskies.mod.compat.flooding.shaders.ShaderWaterVolume;

public final class BslFluidCompat {
    private static final Pattern MAIN = Pattern.compile("(?m)^void main\\(\\)\\s*\\{");
    private static final String WATER_FOG = "vec4 GetWaterFog(";
    private static final String DEFERRED_OUTPUT = "vec3 reflectionColor = pow(color.rgb, vec3(0.125)) * 0.5;";
    private static final String SHAFT_MARCH = "vec3 GetLightShafts(";
    private static final Pattern SHADOW_TINT = Pattern.compile(
        "(?s)vec3 GetShadowTint\\(.*?shadowTint = ((?:vec3\\(currentDist\\)|waterTint)[^;]*);.*?\\n}");
    private static final String SHAFT_TINT_CALL = "vec3 shadowTint = GetShadowTint(translucent, waterTint, currentDist, linearZ0);";
    private static final String SHAFT_POSITION = "vec3 sampleWorldPos = GetSampleWorldPos(texCoord, currentDist);";

    private BslFluidCompat() { }

    public static String vertex(final String name, final String source) {
        if (!NativeFluidCompat.bslSelected() || !surface(name)) return source;
        return MAIN.matcher(source).replaceFirst("""
            uniform bool vs_SimFluid;
            flat out int vs_SimInterface;
            void main() {
                vs_SimInterface = int(vs_SimFluid && mc_Entity.y > 1.5);
            """);
    }

    public static String fragment(final String name, final String source, final Supplier<String> composite) {
        if (!NativeFluidCompat.bslSelected()) return source;
        if (surface(name)) {
            return MAIN.matcher(source).replaceFirst("""
                flat in int vs_SimInterface;
                void main() {
                    if (vs_SimInterface != 0 && !gl_FrontFacing) discard;
                """);
        }
        return switch (name) {
            case "deferred1" -> exteriorFog(source, composite);
            case "composite" -> exteriorShafts(source);
            default -> source;
        };
    }

    private static boolean surface(final String name) {
        return name.equals("gbuffers_water") || name.equals("gbuffers_terrain");
    }

    private static String exteriorFog(final String source, final Supplier<String> composite) {
        if (!source.contains(DEFERRED_OUTPUT)) return source;
        String fog = "";
        if (!source.contains(WATER_FOG)) {
            final String nativeSource = composite.get();
            final int start = nativeSource.indexOf(WATER_FOG);
            final int end = nativeSource.indexOf("\n}", start);
            if (start < 0 || end < 0) return source;
            fog = nativeSource.substring(start, end + 2).replace("fogColor * fogColor * 0.125", "waterAlbedo");
        }
        return MAIN.matcher(source).replaceFirst("""
            uniform bool vs_ExteriorFogEnabled;
            uniform sampler2D vs_ExteriorWaterPath;
            uniform vec3 vs_ExteriorWaterColor;
            %s
            void main() {
            """.formatted(fog)).replace(DEFERRED_OUTPUT, """
            if (vs_ExteriorFogEnabled && isEyeInWater == 0) {
                float vs_WetDistance = texture2D(vs_ExteriorWaterPath, texCoord).r;
                if (vs_WetDistance > 0.001) {
                    vec3 vs_BiomeFog = vs_ExteriorWaterColor * vs_ExteriorWaterColor * 0.125;
                    vec4 vs_Fog = GetWaterFog(vec3(0.0, 0.0, vs_WetDistance), vs_BiomeFog);
                    vec3 vs_Fogged = mix(sqrt(max(color.rgb, vec3(0.0))), sqrt(vs_Fog.rgb), vs_Fog.a);
                    color.rgb = vs_Fogged * vs_Fogged;
                }
            }
            """ + DEFERRED_OUTPUT);
    }

    private static String exteriorShafts(final String source) {
        final int march = source.indexOf(SHAFT_MARCH);
        final var tint = SHADOW_TINT.matcher(source);
        if (march < 0 || !tint.find() || !source.contains(SHAFT_TINT_CALL) || !source.contains(SHAFT_POSITION)) {
            return source;
        }
        final String tintFunction = """
            vec3 GetShadowTint(vec3 translucent, vec3 waterTint, float currentDist, float linearZ0, bool vs_Wet) {
                vec3 shadowTint = currentDist > linearZ0 ? translucent : vec3(1.0);
                if (vs_Wet || (currentDist <= linearZ0 && isEyeInWater == 1.0)) {
                    shadowTint *= %s;
                }
                return shadowTint;
            }
            """.formatted(tint.group(1));
        return source.substring(0, tint.start()) + tintFunction + source.substring(tint.end(), march)
            + ShaderWaterVolume.DECLARATIONS + source.substring(march)
                .replace("vec3 vl = vec3(0.0);", """
                    bool vs_TraceWet = vs_ExteriorFogEnabled && isEyeInWater == 0
                        && texture2D(vs_ExteriorWaterPath, texCoord).r > 0.001;
                    vec3 vl = vec3(0.0);
                    """)
                .replace(SHAFT_TINT_CALL, "")
                .replace(SHAFT_POSITION, SHAFT_POSITION + """

                    bool vs_Wet = vs_TraceWet && vs_wetAt(sampleWorldPos) > 0.5;
                    vec3 shadowTint = GetShadowTint(translucent, waterTint, currentDist, linearZ0, vs_Wet);
                    """);
    }
}
