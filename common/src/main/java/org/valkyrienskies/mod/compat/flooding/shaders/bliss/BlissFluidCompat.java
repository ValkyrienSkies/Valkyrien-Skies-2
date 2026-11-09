package org.valkyrienskies.mod.compat.flooding.shaders.bliss;

import java.util.regex.Pattern;
import org.valkyrienskies.mod.compat.flooding.shaders.NativeFluidCompat;
import org.valkyrienskies.mod.compat.flooding.shaders.ShaderWaterVolume;

public final class BlissFluidCompat {
    private static final String MAIN = "void main() {";
    private static final String WATER_MARCH = "vec4 waterVolumetrics_test(";
    private static final String AIR_MARCH = "vec4 GetVolumetricFog(";
    private static final String WATER_MASK = "bool iswater = texture2D(colortex7,tc).a > 0.99;";
    private static final Pattern RENDER_SCALE = Pattern.compile("\\b(?:tc|texcoord)\\s*/\\s*(vec2\\s*\\([^)]*\\))");
    private static final Pattern WATER_DISPLACEMENT =
        Pattern.compile("if\\(mc_Entity\\.x == 8\\.0\\) \\{(?=\\s*vec3 displacedPos)");

    private BlissFluidCompat() { }

    public static String vertex(final String name, final String source) {
        if (!NativeFluidCompat.blissSelected() || !surface(name)) return source;
        final String patched = source.replace(MAIN, """
            uniform bool vs_SimFluid;
            flat out int vs_SimInterface;
            void main() {
                vs_SimInterface = int(vs_SimFluid && mc_Entity.y > 1.5);
            """);
        return WATER_DISPLACEMENT.matcher(patched).replaceFirst("if(!vs_SimFluid && mc_Entity.x == 8.0) {");
    }

    public static String fragment(final String name, final String source) {
        if (!NativeFluidCompat.blissSelected()) return source;
        if (surface(name)) {
            return source.replace(MAIN, """
                flat in int vs_SimInterface;
                void main() {
                    if (vs_SimInterface != 0 && !gl_FrontFacing) discard;
                """);
        }
        return switch (name) {
            case "composite" -> exteriorWaterFog(source);
            case "composite2" -> blendExteriorFog(source);
            case "composite3" -> maskAirFog(source);
            default -> source;
        };
    }

    private static boolean surface(final String name) {
        return name.equals("gbuffers_water") || name.equals("gbuffers_terrain");
    }

    private static String exteriorWaterFog(final String source) {
        final int start = source.indexOf(WATER_MARCH);
        final int end = source.indexOf("return vec4( vL, dot(newabsorbance,vec3(0.335)));", start);
        final String ray = "vec3 playerPos = normalize(mat3(gbufferModelViewInverse) *  viewPos1);";
        final String step = "vec3 progressW = start.xyz+cameraPosition+dVWorld;";
        final var scale = RENDER_SCALE.matcher(source);
        if (start < 0 || end < 0 || !scale.find() || !source.contains(ray) || !source.contains(WATER_MASK)
            || !source.contains("uniform sampler2DShadow shadow;")) return source;
        final String march = source.substring(start, end);
        if (!march.contains(step)) return source;
        final String wetMarch = march
            .replace("vec3 newabsorbance = ", "if (vs_TraceWet) waterCoefs = max(waterCoefs, vec3(1e-6));\nvec3 newabsorbance = ")
            .replace("min(rayLength,12.0)", "min(rayLength, vs_TraceWet ? 48.0 : 12.0)")
            .replace("ambient = max(ambient * (normalize(wpos).y", """
                vec3 vs_Direction = normalize(mat3(gbufferModelViewInverse) * rayEnd);
                ambient = max(ambient * ((vs_TraceWet ? vs_Direction : normalize(wpos)).y""")
            .replace("float expFactor = 11.0;", "if (vs_TraceWet) spCount = max(spCount, 16);\nfloat expFactor = 11.0;")
            .replace(step, """
                float vs_StepLength = dd * rayLength;
                vec3 progressW = start.xyz+cameraPosition+dVWorld;
                if (vs_TraceWet) {
                    float vs_Start = (pow(expFactor, float(i) / float(spCount)) - 1.0) / (expFactor - 1.0);
                    float vs_End = (pow(expFactor, float(i + 1) / float(spCount)) - 1.0) / (expFactor - 1.0);
                    vs_StepLength = (vs_End - vs_Start) * rayLength
                        * vs_wetSlice(vs_Direction, vs_Start * rayLength, vs_End * rayLength);
                    if (vs_StepLength <= 0.0) continue;
                    progressW = gbufferModelViewInverse[3].xyz + cameraPosition + vs_Direction * (d * rayLength);
                }
                """)
            .replace("vec3 sunMul = exp(-estSunDepth * d * waterCoefs * 1.1);", """
                vec3 sunMul = exp(-estSunDepth * d * waterCoefs * 1.1);
                if (vs_TraceWet && abs(WsunVec.y) > 0.001) {
                    sunMul *= waterCaustics(progressW, WsunVec) * 0.25 + 0.75;
                }
                """)
            .replace("exp(-waterCoefs * dd * rayLength)", "exp(-waterCoefs * vs_StepLength)");
        return source.substring(0, start) + wetDeclarations() + wetMarch
            + source.substring(end)
                .replace("dot(newabsorbance,vec3(0.335))",
                    "vs_TraceWet ? dot(absorbance, vec3(1.0 / 3.0)) : dot(newabsorbance,vec3(0.335))")
                .replace(WATER_MASK, WATER_MASK + "\n" + wetTrace(scale.group(1)))
                .replace("if(texture2D(colortex2, tc).a > 0.0 || iswater){",
                    "if(vs_TraceWet || texture2D(colortex2, tc).a > 0.0 || iswater){")
                .replace(ray, ray + """

                    if (vs_TraceWet) {
                        vec4 vs_WaterFog = waterVolumetrics_test(vec3(0.0), viewPos1, 0.0, 0.0,
                            length(viewPos1), noise_1, totEpsilon, scatterCoef, indirectLightColor_dynamic,
                            directLightColor, dot(normalize(viewPos1), normalize(sunVec * lightCol.a)));
                        gl_FragData[0] = clamp(vs_WaterFog, 0.0, 65000.0);
                        return;
                    }
                    """);
    }

    private static String blendExteriorFog(final String source) {
        final String sky = "gl_FragData[0].rgb = clamp(fp10Dither(Background, triangularize(noise_2)), 0.0, 65000.);";
        final String behind = "if(translucentMasks > 0.0){";
        final String apply = "gl_FragData[0].rgb = gl_FragData[0].rgb * vlBehingTranslucents.a + vlBehingTranslucents.rgb;";
        final int sample = source.indexOf("vec4 vlBehingTranslucents = BilateralUpscale_VLFOG(");
        final int end = source.indexOf(apply, sample);
        final var scale = RENDER_SCALE.matcher(source);
        if (sample < 0 || end < 0 || !scale.find() || !source.contains(sky) || !source.contains(behind)) return source;
        final String wet = """
            (vs_ExteriorFogEnabled && isEyeInWater == 0 && !isWater
                && texture2D(vs_ExteriorWaterPath, texcoord / %s).r > 0.001)
            """.formatted(scale.group(1)).stripTrailing();
        return source.replace(MAIN, """
                uniform bool vs_ExteriorFogEnabled;
                uniform sampler2D vs_ExteriorWaterPath;
                void main() {
                """)
            .replace(behind, "if(translucentMasks > 0.0 || " + wet + "){")
            .replace(sky, sky + "\nif " + wet + " {\n" + source.substring(sample, end + apply.length()) + "\n}\n");
    }

    private static String maskAirFog(final String source) {
        final int start = source.indexOf(AIR_MARCH);
        final String density = "float fogDensity = densityVol;";
        final var scale = RENDER_SCALE.matcher(source);
        if (start < 0 || !scale.find() || !source.contains(density) || !source.contains(WATER_MASK)) return source;
        return source.substring(0, start) + wetDeclarations() + source.substring(start)
            .replace(WATER_MASK, WATER_MASK + "\n" + wetTrace(scale.group(1)))
            .replace(density, """
                float vs_DryFraction = 1.0;
                if (vs_TraceWet) {
                    float vs_Start = (pow(expFactor, float(i) / float(SAMPLECOUNT)) - 1.0)
                        / (expFactor - 1.0) * dL_alternate;
                    float vs_End = (pow(expFactor, float(i + 1) / float(SAMPLECOUNT)) - 1.0)
                        / (expFactor - 1.0) * dL_alternate;
                    float vs_Last = min(vs_End, 48.0);
                    if (vs_Last > vs_Start) {
                        vs_DryFraction -= vs_wetSlice(dVWorld / dL_alternate, vs_Start, vs_Last)
                            * (vs_Last - vs_Start) / (vs_End - vs_Start);
                    }
                }
                densityVol *= vs_DryFraction;
                """ + density)
            .replace("vec3 rL = rC*airCoef.x;", "airCoef *= vs_DryFraction; vec3 rL = rC*airCoef.x;");
    }

    private static String wetDeclarations() {
        return ShaderWaterVolume.DECLARATIONS + "bool vs_TraceWet = false;\n";
    }

    private static String wetTrace(final String scale) {
        return """
            vec2 vs_FogUv = tc / %s;
            vs_TraceWet = vs_ExteriorFogEnabled && isEyeInWater == 0 && !iswater
                && all(lessThan(vs_FogUv, vec2(1.0)))
                && texture2D(vs_ExteriorWaterPath, vs_FogUv).r > 0.001;
            """.formatted(scale);
    }
}
