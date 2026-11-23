package org.valkyrienskies.mod.compat.create;

import java.util.Set;
import org.spongepowered.asm.service.MixinService;

/**
 * Both split-piece glue hooks must apply to the same verified selection loop, so they're only enabled on these Create
 * builds. Other builds get the ray-transform glue selection instead, whose hooks are optional.
 */
public final class GlueHookVersions {
    private static final Set<String> FABRIC = Set.of("6.0.4.0+mc1.20.1-build.1673", "6.0.7.0+mc1.20.1-build.1706", "6.0.7.0+build.1728-mc1.20.1");
    private static boolean warned;
    private GlueHookVersions() {}

    /** Whether the split-piece hooks were verified on this Create build. */
    public static boolean verified(String version, boolean fabric) {
        return fabric ? FABRIC.contains(version) : version.equals("6.0.7-281");
    }

    /** [verified], logging once when it isn't. */
    public static boolean enabled(String version, boolean fabric) {
        boolean known = verified(version, fabric);
        if (!known && !warned) {
            warned = true;
            MixinService.getService().getLogger("mixin").warn(
                "[VS2] Unverified Create {} build {}: using the ray-transform ship glue selection", fabric ? "Fabric" : "Forge", version);
        }
        return known;
    }
}
