package org.valkyrienskies.mod.common.render.light;

import org.lwjgl.opengl.GL20;

public final class VsShipWorldLightTerrainUniforms {

    private VsShipWorldLightTerrainUniforms() {
    }

    public static void setupForProgram(final int programId, final double camX, final double camY,
        final double camZ) {
        VsDynamicLight.getShipEmitterList().bind(VsDynamicLight.SHIP_EMITTER_LIST_TEXTURE_UNIT);
        setInt(programId, "u_VsShipEmitters", VsDynamicLight.SHIP_EMITTER_LIST_TEXTURE_UNIT);
        setInt(programId, "u_VsShipEmitterCount", VsDynamicLight.getShipEmitterList().size());
        setInt(programId, "u_VsShipGlowEnabled", 1);

        final int camLoc = GL20.glGetUniformLocation(programId, "u_VsShipLightCameraPos");
        if (camLoc >= 0) {
            GL20.glUniform3f(camLoc, (float) camX, (float) camY, (float) camZ);
        }
    }

    public static void disableForProgram(final int programId) {
        setInt(programId, "u_VsShipEmitters", VsDynamicLight.SHIP_EMITTER_LIST_TEXTURE_UNIT);
        setInt(programId, "u_VsShipGlowEnabled", 0);
    }

    private static void setInt(final int programId, final String name, final int value) {
        final int loc = GL20.glGetUniformLocation(programId, name);
        if (loc >= 0) {
            GL20.glUniform1i(loc, value);
        }
    }
}
