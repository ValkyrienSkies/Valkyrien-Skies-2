package org.valkyrienskies.mod.forge.mixin;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import java.util.List;
import java.util.Set;
import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Used to fix interact distance on forge 47.4.2 but also not break forge 47.4.0
 */
public class ValkyrienForgeMixinConfigPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(final String s) {
        MixinExtrasBootstrap.init();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String s, final String mixinClassName) {
        final DefaultArtifactVersion forgeVersion = new DefaultArtifactVersion(FMLLoader.versionInfo().forgeVersion());
        final DefaultArtifactVersion forgeNewer = new DefaultArtifactVersion("47.4.2");

        // MixinIForge2Player is the mixin for forge >=47.4.2
        // MixinIForgePlayer is the original mixin, for forge <47.4.2
        if (mixinClassName.contains("MixinIForge2Player")) {
            return forgeVersion.compareTo(forgeNewer) >= 0;
        }
        if (mixinClassName.contains("MixinIForgePlayer")) {
            return forgeVersion.compareTo(forgeNewer) < 0;
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
