package org.valkyrienskies.mod.compat.flooding.shaders.complementary;

import org.valkyrienskies.mod.compat.flooding.shaders.NativeFluidCompat;

public final class ComplementaryFluidCompat {
    private ComplementaryFluidCompat() { }

    public static String vertex(final String name, final String source) {
        if ((!name.equals("gbuffers_water") && !name.equals("gbuffers_terrain")) || !NativeFluidCompat.complementarySelected()) return source;
        return source.replace("void main() {", "uniform bool vs_SimFluid;\nflat out int vs_SimInterface;\nvoid main() {\nvs_SimInterface = mc_Entity.y > 1.5 ? 1 : 0;")
            .replace("DoWave(position.xyz, mat);", "if (!vs_SimFluid) DoWave(position.xyz, mat);");
    }

    public static String fragment(final String name, final String source) {
        if ((!name.equals("gbuffers_water") && !name.equals("gbuffers_terrain")) || !NativeFluidCompat.complementarySelected()) return source;
        return source.replace("void main() {", "flat in int vs_SimInterface;\nvoid main() {\nif (vs_SimInterface != 0 && !gl_FrontFacing) discard;");
    }
}
