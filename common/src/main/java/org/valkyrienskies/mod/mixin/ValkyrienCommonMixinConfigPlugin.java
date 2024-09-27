package org.valkyrienskies.mod.mixin;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
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
        if (classExists("optifine.OptiFineTransformationService")) {
            return VSRenderer.OPTIFINE;
        } else if (classExists("me.jellysquid.mods.sodium.client.SodiumClientMod")) {
            return VSRenderer.SODIUM;
        } else {
            return VSRenderer.VANILLA;
        }
    }

    private static boolean classExists(final String className) {
        try {
            Class.forName(className, false, ValkyrienCommonMixinConfigPlugin.class.getClassLoader());
            return true;
        } catch (final ClassNotFoundException ex) {
            return false;
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
        final VSRenderer renderer = getVSRenderer();
        if (mixinClassName.contains("org.valkyrienskies.mod.mixin.mod_compat.sodium")) {
            return renderer == VSRenderer.SODIUM;
        }
        if (mixinClassName.contains("org.valkyrienskies.mod.mixin.mod_compat.optifine_vanilla")) {
            return renderer == VSRenderer.VANILLA || renderer == VSRenderer.OPTIFINE;
        }
        if (mixinClassName.contains("org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer")) {
            return renderer == VSRenderer.VANILLA;
        }
        if (mixinClassName.contains("org.valkyrienskies.mod.mixin.mod_compat.optifine")) {
            return renderer == VSRenderer.OPTIFINE;
        }
        if (mixinClassName.contains("org.valkyrienskies.mod.mixin.feature.render_pathfinding")) {
            return PATH_FINDING_DEBUG;
        }
        if (mixinClassName.contains("org.valkyrienskies.mod.mixin.mod_compat.create.client.trackOutlines")) {
            //interactive has its own track outline stuff so disable fixed version of VS2's track outline stuff
            if (classExists("org.valkyrienskies.create_interactive.mixin.client.MixinTrackBlockOutline")) {
                MixinService.getService().getLogger("mixin")
                    .info("[VS2] found Interactive, disabling VS2's trackOutline Compat - " +
                        mixinClassName.substring(mixinClassName.lastIndexOf(".") + 1));
                return false;
            }
        }
        // Only load this mixin when ETF is installed
        if (mixinClassName.equals("org.valkyrienskies.mod.mixin.mod_compat.etf.MixinBlockEntity")) {
            if (!classExists("traben.entity_texture_features.utils.ETFEntity")) {
                return false;
            }
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
