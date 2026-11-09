package org.valkyrienskies.mod.forge.mixin;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * NeoForge 1.21.1 only needs the current Create and player-extension mixins.
 */
public class ValkyrienForgeMixinConfigPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(final String s) {

        if (LoadingModList.get().getModFileById("create") != null) {
            final DefaultArtifactVersion createVersion =
                new DefaultArtifactVersion(LoadingModList.get().getModFileById("create").versionString());
            final DefaultArtifactVersion createNewer = new DefaultArtifactVersion("6.0.7");

            is607orAbove = createVersion.compareTo(createNewer) >= 0;
        }

        MixinExtrasBootstrap.init();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String s, final String mixinClassName) {
        if (mixinClassName.contains("MixinIForge2Player")) {
            return false;
        }
        if (mixinClassName.contains("MixinIForgePlayer")) {
            return true;
        }
        if (mixinClassName.contains("MixinEmbeddiumShaderModBridge")) {
            return LoadingModList.get().getModFileById("embeddium") != null;
        }

        if (mixinClassName.contains("MixinSuperGlueSelectionHandler67")) {
            return true;
        }
        if (mixinClassName.contains("MixinSuperGlueSelectionHandler66")) {
            return false;
        }
        if (mixinClassName.contains("MixinBlockEntityRenderHelper67")) {
            return true;
        }
        if (mixinClassName.contains("MixinBlockEntityRenderHelper66")) {
            return false;
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
