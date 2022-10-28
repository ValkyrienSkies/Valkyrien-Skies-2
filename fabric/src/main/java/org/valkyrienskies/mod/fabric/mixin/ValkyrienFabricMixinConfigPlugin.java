package org.valkyrienskies.mod.fabric.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.valkyrienskies.mod.fabric.AutoDependenciesFabric;

/**
 * For now, just using this class as an abusive early entrypoint to run the updater
 */
public class ValkyrienFabricMixinConfigPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(final String s) {
        AutoDependenciesFabric.runUpdater();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String s, final String s1) {
        return false;
    }

    @Override
    public void acceptTargets(final Set<String> set, final Set<String> set1) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String s, final ClassNode classNode, final String s1, final IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(final String s, final ClassNode classNode, final String s1, final IMixinInfo iMixinInfo) {

    }
}
