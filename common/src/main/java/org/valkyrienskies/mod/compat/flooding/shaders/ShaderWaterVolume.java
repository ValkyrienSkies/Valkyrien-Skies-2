package org.valkyrienskies.mod.compat.flooding.shaders;

import org.valkyrienskies.mod.common.fluid.FluidOcclusionShader;

public final class ShaderWaterVolume {
    private ShaderWaterVolume() { }

    public static final String DECLARATIONS = """
            uniform bool vs_ExteriorFogEnabled;
            uniform sampler2D vs_ExteriorWaterPath;
            uniform sampler3D vs_ExteriorWorldWater;
            uniform vec3 vs_WaterCamera;
            uniform vec3 vs_WaterOffset;
            vec3 valkyrienair_CamRelPos;
            """ + FluidOcclusionShader.atlasDecoder() + """
            bool vs_isDryAt(vec3 position) {
                valkyrienair_CamRelPos = position;
                return VsFluidOcclusionEnabled > 0.5 && vs_shouldCullOverflow(VsFluidOverflowDescriptors);
            }
            float vs_wetAt(vec3 position) {
                vec3 grid = vs_WaterCamera + position;
                ivec3 cell = ivec3(floor(grid));
                ivec3 size = textureSize(vs_ExteriorWorldWater, 0);
                if (any(lessThan(cell, ivec3(0))) || any(greaterThanEqual(cell, size))) return 0.0;
                float height = texelFetch(vs_ExteriorWorldWater, (cell + ivec3(vs_WaterOffset)) % size, 0).r;
                if (height <= fract(grid.y)) return 0.0;
                return vs_isDryAt(position) ? 0.0 : 1.0;
            }
            float vs_wetSlice(vec3 direction, float start, float end) {
                float wet = 0.0;
                for (int i = 0; i < 4; i++) wet += vs_wetAt(direction * mix(start, end, (float(i) + 0.5) * 0.25));
                return wet * 0.25;
            }
            """;
}
