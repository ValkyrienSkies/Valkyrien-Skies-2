package org.valkyrienskies.mod.forge.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class ValkyrienSkiesForgeMixinPlugin implements IMixinConfigPlugin {

    private static boolean classExists(final String className) {
        try {
            Class.forName(className, false, ValkyrienSkiesForgeMixinPlugin.class.getClassLoader());
            return true;
        } catch (final ClassNotFoundException ex) {
            return false;
        }
    }

    @Override
    public void onLoad(final String s) {

    }

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(final String s, final String mixinClassName) {
        final boolean isMixinBoosterLoaded = classExists("io.github.steelwoolmc.mixintransmog.MixinModlauncherRemapper");

        if (mixinClassName.contains("org.valkyrienskies.mod.forge.mixin.compat.mixinbooster")) {
            return isMixinBoosterLoaded; // Load only if mixinbooster is enabled
        }
        if (mixinClassName.equals("org.valkyrienskies.mod.forge.mixin.feature.forge_interact.MixinIForgePlayer")) {
            return !isMixinBoosterLoaded; // Load only if mixinbooster is not enabled
        }

        return true;
    }

    @Override
    public void acceptTargets(final Set<String> set, final Set<String> set1) {

    }

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(final String s, final ClassNode classNode, final String s1, final IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(final String s, final ClassNode classNode, final String s1, final IMixinInfo iMixinInfo) {

    }
}
