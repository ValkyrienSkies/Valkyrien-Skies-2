package org.valkyrienskies.mod.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * For now, just using this class as an abusive early entrypoint to run the updater
 */
public class ValkyrienCommonMixinConfigPlugin implements IMixinConfigPlugin {

    private static Boolean hasOptifine = null;

    public static boolean hasOptifine() {
        if (hasOptifine == null) {
            hasOptifine = _hasOptifine();
        }

        return hasOptifine;
    }

    private static boolean _hasOptifine() {
        try {
            Class.forName("optifine.OptiFineTransformationService");
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onLoad(final String s) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String s, final String mixinClassName) {
        if (mixinClassName.equals("org.valkyrienskies.mod.mixin.accessors.client.render.RenderChunkInfoAccessorOptifine")) {
            return hasOptifine();
        }
        if (mixinClassName.equals("org.valkyrienskies.mod.mixin.accessors.client.render.RenderChunkInfoAccessor")) {
            return !hasOptifine();
        }
        return true;
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
