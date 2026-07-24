package org.valkyrienskies.mod.compat.iris;
import net.irisshaders.iris.api.v0.IrisApi;

public class IrisCompat {
    public static boolean isIrisShaderActive() {
        IrisApi irisApi = IrisApi.getInstance();
        return irisApi != null && irisApi.isShaderPackInUse();
    }
}
