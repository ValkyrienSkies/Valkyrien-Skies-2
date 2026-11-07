package org.valkyrienskies.mod.common.fluid.client;

import net.minecraft.resources.ResourceLocation;

/**
 * Splits vanilla's {@code "shaders/(core|program)/" + name + suffix} concat back into a namespaced
 * {@link ResourceLocation} when the caller passed a {@code "namespace:name"} string. Forge patches this
 * directly in {@code EffectInstance} / {@code ShaderInstance}; Fabric has no equivalent patch, so we
 * redirect the {@code new ResourceLocation(String)} calls at the use site.
 */
public final class ShaderNamespaceHelper {
    private ShaderNamespaceHelper() {
    }

    public static ResourceLocation splitNamespacedShaderPath(final String combined, final String prefix) {
        final int colonIdx = combined.indexOf(':');
        if (colonIdx < 0 || !combined.startsWith(prefix) || colonIdx <= prefix.length()) {
            return new ResourceLocation(combined);
        }
        final String namespace = combined.substring(prefix.length(), colonIdx);
        final String remainder = combined.substring(colonIdx + 1);
        return new ResourceLocation(namespace, prefix + remainder);
    }
}
