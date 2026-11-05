package org.valkyrienskies.mod.compat.flooding.shaders.photon;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.valkyrienskies.mod.compat.flooding.shaders.NativeFluidCompat;
import org.valkyrienskies.mod.compat.flooding.shaders.ShaderWaterVolume;

public final class PhotonFluidCompat {
    private static final String MAIN = "void main() {";
    private static final String WATER_MARCH = "mat2x3 raymarch_water_fog(";
    private static final String FOG_TARGET = "layout(location = 0) out vec3 fog_transmittance;";
    private static final String FOG_OUTPUT = "fog_transmittance = fog[1];";
    private static final String NO_FOG_OUTPUT = "fog_transmittance = vec3(1.0);";
    private static final String FOG_ENDPOINT = "vec3 world_end_pos = world_pos;";
    private static final String FOG_SAMPLE = "vec3 fog_transmittance = smooth_filter(colortex6, uv).rgb;";
    private static final String FOG_BLEND = "fragment_color = fragment_color * fog_transmittance + fog_scattering;";
    private static final String TRACE_WET = """
        vs_TraceWet = vs_ExteriorFogEnabled && isEyeInWater == 0
            && texture(vs_ExteriorWaterPath, uv).r > 0.001;
        vec3 vs_WetEnd = world_end_pos;
        bool vs_WetSky = depth0 == 1.0;
        if (vs_TraceWet && depth0 < depth1) {
            vec3 vs_Front = scene_pos - gbufferModelViewInverse[3].xyz;
            float vs_FrontDistance = length(vs_Front);
            if (vs_FrontDistance > 0.0
                && vs_isDryAt(scene_pos - vs_Front * min(0.15 / vs_FrontDistance, 1.0))) {
                vs_WetEnd = world_back_pos;
                vs_WetSky = depth1 == 1.0;
                vs_FogBehind = true;
            }
        }
        """;
    private static final String WATER_FOG_CALL = "raymarch_water_fog(world_start_pos, vs_WetEnd, vs_WetSky, dither)";
    private static final Pattern BLEND_LAYERS = Pattern.compile("fragment_color = blend_layers_with_fog\\([^;]+\\);");

    private PhotonFluidCompat() { }

    public static String vertex(final String name, final String source) {
        if (!surface(name) || !source.contains(MAIN)) return source;
        return source.replace(MAIN, """
            uniform bool vs_SimFluid;
            flat out int vs_SimInterface;
            void main() {
                vs_SimInterface = int(vs_SimFluid && mc_Entity.y > 1.5);
            """)
            .replace("position_scene = animate_vertex(", "if (!vs_SimFluid) position_scene = animate_vertex(");
    }

    public static String fragment(final String name, final String source) {
        if (surface(name)) {
            return source.replace(MAIN, """
                flat in int vs_SimInterface;
                void main() {
                    if (vs_SimInterface != 0 && !gl_FrontFacing) discard;
                """);
        }
        if (!NativeFluidCompat.photonSelected()) return source;
        return switch (name) {
            case "composite" -> volumetrics(source);
            case "composite1" -> blendFog(source);
            default -> source;
        };
    }

    private static boolean surface(final String name) {
        return (name.equals("gbuffers_water") || name.equals("gbuffers_terrain") || name.equals("gbuffers_terrain_solid"))
            && NativeFluidCompat.photonSelected();
    }

    private static String volumetrics(final String source) {
        final int start = source.indexOf(WATER_MARCH);
        final String marchResult = "return mat2x3(scattering, transmittance);";
        final int end = source.indexOf(marchResult, start);
        final int declarations = source.indexOf(FOG_TARGET);
        final String shadowSample = "vec3 shadow_screen_pos = distort_shadow_space(shadow_pos) * 0.5 + 0.5;";
        final String nativeWeight = "scattering\n        *= (1.0 - step_transmittance) * scattering_coeff / extinction_coeff;";
        if (declarations < 0 || start < 0 || end < 0 || !source.contains(FOG_ENDPOINT) || !source.contains(MAIN)) return source;
        final String march = source.substring(start, end);
        if (!march.contains(shadowSample) || !march.contains(nativeWeight)) return source;

        final String wetMarch = march
            .replace("float step_length = ray_length", """
                if (vs_TraceWet) ray_length = min(ray_length, vs_WetRange);
                    float step_length = ray_length""")
            .replace(shadowSample, """
                vec3 vs_ScatterWeight = vec3(1.0);
                if (vs_TraceWet) {
                    float vs_Start = float(i) * step_length;
                    float vs_WetLength = step_length * vs_wetSlice(world_dir, vs_Start, vs_Start + step_length);
                    if (vs_WetLength <= 0.0) continue;
                    step_transmittance = exp(-extinction_coeff * vs_WetLength);
                    vs_ScatterWeight = (1.0 - step_transmittance) * scattering_coeff / extinction_coeff;
                }
                """ + shadowSample)
            .replace("scattering_amount;", "scattering_amount * vs_ScatterWeight;")
            .replace("scattering_amount * sky_transmittance;", "scattering_amount * sky_transmittance * vs_ScatterWeight;")
            .replace(nativeWeight, "if (!vs_TraceWet) " + nativeWeight);
        final String patched = source.substring(0, start) + wetMarch + source.substring(end);
        final String header = patched.substring(0, declarations) + ShaderWaterVolume.DECLARATIONS
            + """
            const float vs_WetRange = 48.0;
            bool vs_TraceWet = false;
            bool vs_FogBehind = false;
            vec3 fog_transmittance = vec3(1.0);
            """;
        final String body = patched.substring(declarations)
            .replace(FOG_TARGET, "layout(location = 0) out vec4 vs_FogData;")
            .replace(FOG_ENDPOINT, FOG_ENDPOINT + "\n" + TRACE_WET)
            .replace(MAIN, "void vs_PhotonFogMain() {") + """

            void main() {
                vs_PhotonFogMain();
                vs_FogData = vec4(fog_transmittance, float(vs_FogBehind));
            }
            """;
        if (!body.contains(FOG_OUTPUT)) {
            return header + body.replace(NO_FOG_OUTPUT, NO_FOG_OUTPUT + "\n"
                + "if (vs_TraceWet) fog_transmittance = " + WATER_FOG_CALL + "[1];\n");
        }
        return header + body
                .replace("vec2 density = air_fog_density(world_pos) * step_length;", """
                    vec2 density = air_fog_density(world_pos) * step_length;
                    if (vs_TraceWet) {
                        float vs_Start = distance_to_volume_start + float(i) * step_length;
                        float vs_End = min(vs_Start + step_length, vs_WetRange);
                        if (vs_End > vs_Start) {
                            density *= 1.0 - vs_wetSlice(world_dir, vs_Start, vs_End) * (vs_End - vs_Start) / step_length;
                        }
                    }
                    """)
                .replace(FOG_OUTPUT, FOG_OUTPUT + ("\n" + """
                    if (vs_TraceWet) {
                        mat2x3 vs_WaterFog = %s;
                        fog_scattering += fog_transmittance * vs_WaterFog[0];
                        fog_transmittance *= vs_WaterFog[1];
                    }
                    """.formatted(WATER_FOG_CALL)));
    }

    private static String blendFog(final String source) {
        final boolean hasFog = source.contains(FOG_BLEND);
        if (hasFog && !source.contains(FOG_SAMPLE)) return source;
        final String patched = hasFog ? source.replace(FOG_SAMPLE, """
            vec4 vs_FogData = smooth_filter(colortex6, uv);
            vec3 fog_transmittance = vs_FogData.rgb;
            """).replace(FOG_BLEND, "if (!vs_FogBehind) " + FOG_BLEND) : source;
        final Matcher layers = BLEND_LAYERS.matcher(patched);
        if (!layers.find()) return source;
        final String fog = hasFog ? "" : """
            vec4 vs_FogData = smooth_filter(colortex6, uv);
            vec3 fog_transmittance = vs_FogData.rgb;
            vec3 fog_scattering = vec3(0.0);
            """;
        final String behind = """
            bool vs_FogBehind = is_translucent && !front_is_hand && vs_FogData.a > 0.5;
            if (vs_FogBehind) %s
            """.formatted(FOG_BLEND);
        final String foreground = hasFog ? "" : "\nif (!vs_FogBehind) " + FOG_BLEND;
        return layers.replaceFirst(Matcher.quoteReplacement(fog + behind + layers.group() + foreground));
    }
}
