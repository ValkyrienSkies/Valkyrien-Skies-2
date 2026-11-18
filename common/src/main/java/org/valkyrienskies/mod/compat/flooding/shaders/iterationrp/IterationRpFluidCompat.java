package org.valkyrienskies.mod.compat.flooding.shaders.iterationrp;

import java.util.regex.Pattern;
import net.irisshaders.iris.Iris;
import org.valkyrienskies.mod.compat.flooding.shaders.NativeFluidCompat;
import org.valkyrienskies.mod.compat.flooding.shaders.ShaderWaterVolume;

public final class IterationRpFluidCompat {
    private static final Pattern MAIN = Pattern.compile("(?m)^void main\\(\\)\\s*\\{");
    private static final String AIR_MARCH = "void VolumetricFog(";
    private static final String AIR_ABSORPTION = "float absorption = exp2(-density * stepDensity);";
    private static final String WATER_MARCH = "vec3 UnderwaterVolumetricFog(";
    private static final String GLASS_TINT = "vec3 glassTint = vec3(1.0);";
    private static final String PARTICLE_LIGHTING = "if (materialMask.particle > 0.5){\n\t\tParticleLighting(";
    private static final String WET_ENABLED = """
        bool vs_TraceExteriorWater() {
            return vs_ExteriorFogEnabled && isEyeInWater == 0
                && texture(vs_ExteriorWaterPath, texCoord).r > 0.001;
        }
        """;

    private IterationRpFluidCompat() { }

    public static String vertex(final String name, final String source) {
        if (!NativeFluidCompat.iterationRpSelected() || !surface(name)) return source;
        final String output = name.equals("gbuffers_terrain") ? "vs_TerrainInterface" : "vs_SimInterface";
        return MAIN.matcher(source).replaceFirst("""
            uniform bool vs_SimFluid;
            flat out int %1$s;
            void main() {
                %1$s = int(vs_SimFluid && mc_Entity.y > 1.5);
            """.formatted(output));
    }

    public static String geometry(final String name, final String source) {
        if (!NativeFluidCompat.iterationRpSelected() || !name.equals("gbuffers_terrain")) return source;
        return MAIN.matcher(source).replaceFirst("""
            flat in int vs_TerrainInterface[];
            flat out int vs_SimInterface;
            void main() {
            """).replace("EmitVertex();", "vs_SimInterface = vs_TerrainInterface[0];\nEmitVertex();");
    }

    public static String fragment(final String name, final String source) {
        if (!NativeFluidCompat.iterationRpSelected()) return source;
        if (surface(name)) {
            return MAIN.matcher(source).replaceFirst("""
                flat in int vs_SimInterface;
                void main() {
                    if (vs_SimInterface != 0 && !gl_FrontFacing) discard;
                """);
        }
        return switch (name) {
            case "composite8" -> exteriorFog(source);
            case "composite9" -> maskAirFog(source);
            default -> source;
        };
    }

    private static boolean surface(final String name) {
        return name.equals("gbuffers_terrain") || name.equals("gbuffers_water");
    }

    private static String exteriorFog(final String source) {
        final int waterStart = source.indexOf(WATER_MARCH);
        final int waterEnd = source.indexOf("return result;", waterStart);
        if (waterStart < 0 || waterEnd < 0 || !source.contains(GLASS_TINT) || !source.contains(PARTICLE_LIGHTING)) return source;
        final boolean extinction = source.contains("WaterFog(color, worldDir, waterDeep,");
        final boolean shafts = Iris.getCurrentPack().map(pack -> pack.getShaderPackOptions().getOptionValues()
            .getBooleanValueOrDefault("UNDERWATER_VFOG")).orElse(true);
        if (!extinction && !shafts) return source;

        final String trace = source.substring(waterStart, waterEnd)
            .replace(WATER_MARCH, "vec4 vs_TraceWetVolume(")
            .replace("const float range = 20.0;", "const float range = 48.0;")
            .replace("vec3 result = vec3(0.0);", "vec3 result = vec3(0.0);\nfloat vs_WetDistance = 0.0;")
            .replace("vec3 shadowPos = shadowRayPosition;", """
                float vs_Start = length(startPos) + float(i) * rayIncrement;
                float vs_WetStep = rayIncrement * vs_wetSlice(worldDir, vs_Start, vs_Start + rayIncrement);
                if (vs_WetStep <= 0.0) continue;
                vs_WetDistance += vs_WetStep;
                vec3 shadowPos = shadowRayPosition;
                """)
            .replace("causticsSum += caustics * rayIncrement;", "causticsSum += caustics * vs_WetStep;")
            + "return vec4(" + (shafts ? "result" : "vec3(0.0)") + ", vs_WetDistance);\n}\n";
        final String fog = extinction ? """
            if (vs_Water.a > 0.0) WaterFog(color, vs_Direction, vs_Water.a, -vs_Direction, vs_VdotSR, false, 1.0);
            """ : "";
        final String background = """
            bool vs_FrontWater = materialMask.water > 0.5 || waterData.w > 0.5;
            if (!vs_FrontWater && vs_TraceExteriorWater()) {
                vec3 vs_Back = mat3(gbufferModelViewInverse) * ViewPos_From_ScreenPos(refractCoord, refractDepth);
                vs_ApplyWetFog(color, vs_Back);
            }
            """;
        final String foreground = """
            if (vs_FrontWater && vs_TraceExteriorWater()) vs_ApplyWetFog(color, worldPos);
            """;
        final String helper = """
            void vs_ApplyWetFog(inout vec3 color, vec3 endPos) {
                if (dot(endPos, endPos) <= 0.0) return;
                vec3 vs_Direction = normalize(endPos);
                vec3 vs_Light = refract(-worldShadowVector, vec3(0.0, 1.0, 0.0), 1.0 / 1.2);
                float vs_VdotSR = dot(vs_Light, vs_Direction);
                vec4 vs_Water = vs_TraceWetVolume(vec3(0.0), endPos, vs_Direction, vs_VdotSR, 1.0 - wetness);
                %s
                color += vs_Water.rgb;
            }
            """.formatted(fog);
        final String declarations = source.contains(AIR_MARCH) && source.contains(AIR_ABSORPTION)
            ? "" : ShaderWaterVolume.DECLARATIONS + WET_ENABLED;
        return maskAirFog(source.substring(0, waterStart) + declarations + trace + helper + source.substring(waterStart)
            .replace(GLASS_TINT, background + GLASS_TINT)
            .replace(PARTICLE_LIGHTING, foreground + PARTICLE_LIGHTING));
    }

    private static String maskAirFog(final String source) {
        final int start = source.indexOf(AIR_MARCH);
        if (start < 0 || !source.contains(AIR_ABSORPTION)) return source;
        return source.substring(0, start) + ShaderWaterVolume.DECLARATIONS + WET_ENABLED
            + source.substring(start).replace(AIR_ABSORPTION, """
                vec3 vs_FogPosition = fogPos - cameraPosition;
                if (dot(vs_FogPosition, vs_FogPosition) < 2304.0 && vs_TraceExteriorWater()) {
                    stepDensity *= 1.0 - vs_wetAt(vs_FogPosition);
                }
                """ + AIR_ABSORPTION);
    }
}
