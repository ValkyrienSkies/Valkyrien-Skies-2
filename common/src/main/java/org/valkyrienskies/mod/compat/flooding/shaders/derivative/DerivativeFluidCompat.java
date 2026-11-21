package org.valkyrienskies.mod.compat.flooding.shaders.derivative;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.valkyrienskies.mod.compat.flooding.shaders.NativeFluidCompat;
import org.valkyrienskies.mod.compat.flooding.shaders.ShaderWaterVolume;

public final class DerivativeFluidCompat {
    private static final String MAIN = "void main() {";
    private static final String WATER_LIGHT = "vec3 UnderwaterVolumetricLight(";
    private static final Pattern LIGHT_RESULT = Pattern.compile("return (fogColor \\* [^;]+);");
    private static final String FOG_REGION = "if (any(greaterThanEqual(screenCoord, vec2(0.5)))) return;";
    private static final String WET_RANGE = "const float vs_WetRange = 48.0;\n";
    private static final Pattern SCENE_FETCH = Pattern.compile("texelFetch\\(colortex4, (refractTexel[RGB]?|texel), 0\\)");
    private static final Pattern WAVE_HEIGHT = Pattern.compile("gl_Vertex\\.y \\+ (physics_waveHeight\\([^)]*\\))");
    private static final Pattern REFLECTION_RESULT =
        Pattern.compile("return ([^;]*vec4\\(reflection \\* specular, 1\\.0 - specular\\)[^;]*);");
    private static final String FOG_DEPTH = """
        bool vs_SurfaceFog(ivec2 pixel) {
            int id = int(texelFetch(colortex7, pixel, 0).z * 255.0);
            TranslucentMask mask = CalculateMasksT(id);
            return mask.water || mask.ice;
        }

        float vs_FogDepth(ivec2 pixel, bool surface) {
            return surface ? texelFetch(depthtex0, pixel, 0).x : texelFetch(depthtex1, pixel, 0).x;
        }

        ivec2 vs_FogTileSize() {
            return (ivec2(screenSize) + 1) / 2;
        }
        """;

    private DerivativeFluidCompat() { }

    public static String vertex(final String name, final String source) {
        if (!NativeFluidCompat.derivativeSelected() || !surface(name)) return source;
        final String patched = source.replace(MAIN, """
            uniform bool vs_SimFluid;
            flat out int vs_SimInterface;
            void main() {
                vs_SimInterface = int(vs_SimFluid && mc_Entity.y > 1.5);
            """);
        return WAVE_HEIGHT.matcher(patched).replaceFirst("gl_Vertex.y + (vs_SimFluid ? 0.0 : $1)");
    }

    public static String fragment(final String name, final String source) {
        if (!NativeFluidCompat.derivativeSelected()) return source;
        if (surface(name)) {
            final String patched = source.replace(MAIN, """
                flat in int vs_SimInterface;
                void main() {
                    if (vs_SimInterface != 0 && !gl_FrontFacing) discard;
                """);
            return name.equals("gbuffers_water") ? markSkyReflections(patched) : patched;
        }
        return switch (name) {
            case "composite" -> traceFog(source);
            case "composite1" -> blendFog(source);
            default -> source;
        };
    }

    private static boolean surface(final String name) {
        return name.equals("gbuffers_terrain") || name.equals("gbuffers_water");
    }

    private static String markSkyReflections(final String source) {
        if (!source.contains("vec4 skyboxData = textureBicubic(colortex5, ProjectSky(rayDirWorld)")) return source;
        return REFLECTION_RESULT.matcher(source).replaceFirst("""
            vec4 vs_Reflection = $1;
            if (materialIDs == 16u && !hit) vs_Reflection.a = -1.0 - vs_Reflection.a;
            return vs_Reflection;
            """);
    }

    private static String traceFog(final String source) {
        final int air = source.indexOf("vec4 CalculateVolumetricFog(");
        final int start = source.indexOf(WATER_LIGHT);
        final Matcher result = LIGHT_RESULT.matcher(source);
        if (air < 0 || start <= air || !result.find(start) || !source.contains(FOG_REGION)) return source;
        final String nativeMarch = source.substring(start, result.start());
        final String step = "while (++i < steps) {";
        if (!nativeMarch.contains(step) || !nativeMarch.contains("transmittance *= stepTransmittance;")) return source;
        final boolean shafts = source.contains("if (isEyeInWater == 1) fogData.rgb = UnderwaterVolumetricLight(");
        final String march = nativeMarch
            .replace(WATER_LIGHT, "vec4 vs_TraceExteriorWater(")
            .replace("min(24.0, length(worldPos))", "min(vs_WetRange, length(worldPos))")
            .replace("uint i = 0u;", "float vs_WetDistance = 0.0;")
            .replace(step, """
                for (uint i = 0u; i < steps; ++i) {
                    float vs_Start = float(i) * stepLength;
                    float vs_Length = stepLength * vs_wetSlice(worldDir, vs_Start, vs_Start + stepLength);
                    if (vs_Length <= 0.0) continue;
                    vs_WetDistance += vs_Length;
                    stepTransmittance = exp(-coeff * vs_Length);
                """)
            .replace("shadowPosition += shadowStep;", "shadowPosition = shadowStart + (float(i) + dither) * shadowStep;")
            .replace("if (saturate(shadowProjPos) != shadowProjPos) continue;", """
                if (saturate(shadowProjPos) != shadowProjPos) {
                    transmittance *= stepTransmittance;
                    continue;
                }
                """)
            + "return vec4(" + (shafts ? result.group(1) : "vec3(0.0)") + ", vs_WetDistance / vs_WetRange);\n}\n";
        final String airMarch = source.substring(air, start)
            .replace("fogDensity *= stepLength;", """
                fogDensity *= stepLength;
                if (vs_MaskAir) {
                    float vs_Step = length(rayStep);
                    float vs_Start = float(i) * vs_Step;
                    float vs_End = min(vs_Start + vs_Step, vs_WetRange);
                    if (vs_End > vs_Start) {
                        fogDensity *= 1.0 - vs_wetSlice(worldDir, vs_Start, vs_End) * (vs_End - vs_Start) / vs_Step;
                    }
                }
                """);
        return source.substring(0, air) + ShaderWaterVolume.DECLARATIONS + WET_RANGE
            + "bool vs_MaskAir = false;\n" + airMarch + march + source.substring(start)
                .replace(MAIN, FOG_DEPTH + "\n" + MAIN)
                .replace(FOG_REGION, """
                    ivec2 vs_Tile = vs_FogTileSize();
                    if (vs_ExteriorFogEnabled && isEyeInWater == 0 && texel.x >= vs_Tile.x && texel.y < vs_Tile.y) {
                        fogData = vec4(0.0);
                        ivec2 vs_Pixel = (texel - ivec2(vs_Tile.x, 0)) * 2;
                        vec2 vs_Uv = (vec2(vs_Pixel) + 0.5) * screenPixelSize;
                        if (texture(vs_ExteriorWaterPath, vs_Uv).r > 0.001) {
                            float vs_Depth = vs_FogDepth(vs_Pixel, vs_SurfaceFog(vs_Pixel));
                            vec3 vs_View = ScreenToViewSpaceRaw(vec3(vs_Uv, vs_Depth));
                            vec3 vs_World = mat3(gbufferModelViewInverse) * vs_View;
                            float vs_Dither = R1(frameCounter, texelFetch(noisetex, vs_Pixel & 255, 0).a);
                            fogData = vs_TraceExteriorWater(vs_World, normalize(vs_World), vs_Dither);
                        }
                        return;
                    }
                    if (any(greaterThanEqual(texel, vs_Tile))) return;
                    """)
                .replace("vec3 viewPos = ScreenToViewSpaceRaw(vec3(screenCoord, depth));", """
                    vs_MaskAir = vs_ExteriorFogEnabled && isEyeInWater == 0
                        && texture(vs_ExteriorWaterPath, screenCoord).r > 0.001;
                    vec3 viewPos = ScreenToViewSpaceRaw(vec3(screenCoord, depth));
                    """);
    }

    private static String blendFog(final String source) {
        if (!source.contains(MAIN) || !source.contains("TransparentAbsorption(sceneData, albedoT);")
            || !source.contains("CommonFog(sceneData, fogDist);")) return source;
        return source.replace("in vec2 screenCoord;", """
            uniform bool vs_ExteriorFogEnabled;
            uniform sampler2D vs_ExteriorWaterPath;
            in vec2 screenCoord;
            """).replace("ivec2 sampleTexel = texel + offset[i];", """
                ivec2 sampleTexel = texel + offset[i];
                if (vs_ExteriorFogEnabled && isEyeInWater == 0) {
                    sampleTexel = clamp(sampleTexel, ivec2(0), (ivec2(screenSize) + 1) / 2 - 1);
                }
                """).replace(MAIN, WET_RANGE + FOG_DEPTH + """
            vec3 vs_ApplyExteriorFog(vec3 color, ivec2 pixel, bool surface) {
                if (!vs_ExteriorFogEnabled || isEyeInWater != 0) return color;
                pixel = clamp(pixel, ivec2(0), ivec2(screenSize) - 1);
                vec2 uv = (vec2(pixel) + 0.5) * screenPixelSize;
                if (texture(vs_ExteriorWaterPath, uv).r <= 0.001 || surface != vs_SurfaceFog(pixel)) return color;
                float depth = GetDepthLinear(vs_FogDepth(pixel, surface));
                float tolerance = max(0.15, depth * 0.035);
                ivec2 tile = vs_FogTileSize();
                ivec2 first = pixel / 2;
                vec2 fraction = fract(vec2(pixel) * 0.5);
                ivec2 last = ivec2(screenSize.x, tile.y) - ivec2(tile.x, 0) - 1;
                if (any(lessThan(last, ivec2(0)))) return color;
                vec4 fog = vec4(0.0);
                float totalWeight = 0.0;
                for (int y = 0; y < 2; ++y) for (int x = 0; x < 2; ++x) {
                    ivec2 sampleTexel = clamp(first + ivec2(x, y), ivec2(0), last);
                    ivec2 samplePixel = sampleTexel * 2;
                    if (surface != vs_SurfaceFog(samplePixel)) continue;
                    float error = abs(GetDepthLinear(vs_FogDepth(samplePixel, surface)) - depth);
                    if (error >= tolerance) continue;
                    vec2 bilinear = mix(1.0 - fraction, fraction, vec2(x, y));
                    float weight = (1.0 - error / tolerance) * bilinear.x * bilinear.y;
                    fog += texelFetch(colortex1, sampleTexel + ivec2(tile.x, 0), 0) * weight;
                    totalWeight += weight;
                }
                if (totalWeight <= 0.0) return color;
                fog /= totalWeight;
                if (fog.a <= 0.0) return color;
                vec3 direction = normalize(mat3(gbufferModelViewInverse)
                    * ScreenToViewSpaceRaw(vec3(uv, vs_FogDepth(pixel, surface))));
                TranslucentMask water = CalculateMasksT(17);
                WaterFog(color, water, 1.0, dot(worldLightVector, direction), fog.a * vs_WetRange);
                return color + fog.rgb;
            }
            """ + MAIN)
            .transform(patched -> SCENE_FETCH.matcher(patched).replaceAll(match -> Matcher.quoteReplacement(
                "vec4(vs_ApplyExteriorFog(" + match.group() + ".rgb, " + match.group(1) + ", false), 1.0)")))
            .replace("sceneData = sceneData * reflectionData.a + reflectionData.rgb;", """
                if (materialMaskT.stainedGlass && reflectionData.a < 0.0) {
                    reflectionData.a = -1.0 - reflectionData.a;
                    if (vs_ExteriorFogEnabled && isEyeInWater == 0
                        && texture(vs_ExteriorWaterPath, screenCoord).r > 0.001) {
                        reflectionData.rgb = vec3(0.0);
                    }
                }
                sceneData = sceneData * reflectionData.a + reflectionData.rgb;
                """)
            .replace("CommonFog(sceneData, fogDist);", """
                if (materialMaskT.water || materialMaskT.ice) sceneData = vs_ApplyExteriorFog(sceneData, texel, true);
                CommonFog(sceneData, fogDist);
                """);
    }
}
