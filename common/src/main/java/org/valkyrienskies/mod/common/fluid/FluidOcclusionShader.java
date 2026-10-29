package org.valkyrienskies.mod.common.fluid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class FluidOcclusionShader {
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{");
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\r\\n]*", Pattern.DOTALL);
    private static final String MARKER = "vs_fluidOriginalMain";

    private FluidOcclusionShader() { }

    public static boolean canPatch(final String source) {
        return source != null && MAIN.matcher(COMMENTS.matcher(source).replaceAll(" ")).find();
    }

    public static String vertex(final String source, final boolean iris) {
        if (source.contains(MARKER)) return source;
        return wrap(source) + "\nout vec3 vs_fluidCameraRelative;\nout vec2 vs_fluidUv;\n"
            + "void main() {\n" + MARKER + "();\nvs_fluidCameraRelative = "
            + (iris ? "getVertexPosition().xyz" : "_vert_position + u_RegionOffset + _get_draw_translation(_draw_id)")
            + ";\nvs_fluidUv = _vert_tex_diffuse_coord;\n}\n";
    }

    public static String fragment(final String source) {
        if (source.contains(MARKER)) return source;
        return wrap(source) + "\nin vec3 vs_fluidCameraRelative;\nin vec2 vs_fluidUv;\n"
            + "#define VS_FLUID_ATLAS_ONLY\n#define valkyrienair_CamRelPos vs_fluidCameraRelative\n"
            + Decoder.SOURCE + "\nvoid main() {\n"
            + "if (VsFluidOcclusionEnabled > 0.5 && vs_isFluidSprite(vs_fluidUv) && "
            + "vs_shouldCullOverflow(VsFluidOverflowDescriptors)) discard;\n"
            + MARKER + "();\n}\n";
    }

    private static String wrap(final String source) {
        final String withoutComments = COMMENTS.matcher(source).replaceAll(" ");
        final var main = MAIN.matcher(withoutComments);
        if (!main.find()) throw new IllegalArgumentException("Terrain shader has no main function");
        return main.replaceFirst("void " + MARKER + "() {");
    }

    private static final class Decoder {
        private static final String SOURCE = load();

        private static String load() {
            try (var stream = FluidOcclusionShader.class.getResourceAsStream(
                "/assets/minecraft/shaders/include/vs_fluid_occlusion.glsl")) {
                if (stream == null) throw new IllegalStateException("Missing fluid mask shader");
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (final IOException exception) {
                throw new IllegalStateException("Unable to read fluid mask shader", exception);
            }
        }
    }
}
