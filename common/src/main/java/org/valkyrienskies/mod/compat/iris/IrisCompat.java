package org.valkyrienskies.mod.compat.iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.gl.blending.DepthColorStorage;

public class IrisCompat {
    public static void unlockDepthColor() {
        DepthColorStorage.unlockDepthColor();
    }

    public static boolean isIrisShaderActive() {
        IrisApi irisApi = IrisApi.getInstance();
        return irisApi != null && irisApi.isShaderPackInUse();
    }
}
