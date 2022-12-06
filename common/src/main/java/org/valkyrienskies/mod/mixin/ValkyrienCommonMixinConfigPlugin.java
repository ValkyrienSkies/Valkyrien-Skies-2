package org.valkyrienskies.mod.mixin;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.valkyrienskies.mod.compat.VSRenderer;

/**
 * Used to detect Optifine and apply/not apply Optifine compatible mixins
 */
public class ValkyrienCommonMixinConfigPlugin implements IMixinConfigPlugin {

    private static final boolean PATH_FINDING_DEBUG =
        "true".equals(System.getProperty("org.valkyrienskies.render_pathfinding"));
    private static VSRenderer vsRenderer = null;

    public static VSRenderer getVSRenderer() {
        if (vsRenderer == null) {
            vsRenderer = getVSRendererHelper();
        }
        return vsRenderer;
    }

    private static VSRenderer getVSRendererHelper() {
        try {
            Class.forName("optifine.OptiFineTransformationService");
            return VSRenderer.OPTIFINE;
        } catch (final ClassNotFoundException e) {
            try {
                Class.forName("me.jellysquid.mods.sodium.client.SodiumClientMod");
                System.out.println("SODIUM RENDER DETECTE");
                return VSRenderer.SODIUM;
            } catch (final ClassNotFoundException e2) {
                return VSRenderer.VANILLA;
            }
        }
    }

    @Override
    public void onLoad(final String s) {
        MixinExtrasBootstrap.init();
        Mixins.registerErrorHandlerClass("org.valkyrienskies.mod.mixin.ValkyrienMixinErrorHandler");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String s, final String mixinClassName) {
        if (mixinClassName.contains("org.valkyrienskies.mod.mixin.mod_compat.sodium")) {
            return getVSRenderer() == VSRenderer.SODIUM;
        }
        if (mixinClassName.contains("org.valkyrienskies.mod.mixin.mod_compat.optifine_vanilla")) {
            return getVSRenderer() != VSRenderer.SODIUM;
        }
        if (mixinClassName.contains("org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer")) {
            return getVSRenderer() == VSRenderer.VANILLA;
        }
        if (mixinClassName.contains("org.valkyrienskies.mod.mixin.mod_compat.optifine")) {
            return getVSRenderer() == VSRenderer.OPTIFINE;
        }
        if (mixinClassName.contains("org.valkyrienskies.mod.mixin.feature.render_pathfinding")) {
            return PATH_FINDING_DEBUG;
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
