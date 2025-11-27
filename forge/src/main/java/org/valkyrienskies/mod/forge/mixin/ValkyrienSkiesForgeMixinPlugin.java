package org.valkyrienskies.mod.forge.mixin;

import java.util.List;
import java.util.Set;
import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.valkyrienskies.mod.compat.LoadedMods;

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
        final DefaultArtifactVersion forgeVersion = new DefaultArtifactVersion(FMLLoader.versionInfo().forgeVersion());
        final DefaultArtifactVersion forgeNewer = new DefaultArtifactVersion("47.4.2");

        if (mixinClassName.contains("org.valkyrienskies.mod.forge.mixin.compat.mixinbooster")) {
            return isMixinBoosterLoaded; // Load only if mixinbooster is enabled
        }
        if (mixinClassName.equals("org.valkyrienskies.mod.forge.mixin.feature.forge_interact.MixinIForgePlayer")) {
            return !isMixinBoosterLoaded; // Load only if mixinbooster is not enabled
        }
        if (mixinClassName.contains("MixinIForge2Player")) {
            return forgeVersion.compareTo(forgeNewer) >= 0;
        }
        if (mixinClassName.contains("MixinIForgePlayer")) {
            return forgeVersion.compareTo(forgeNewer) < 0;
        }
        if (mixinClassName.contains("org.valkyrienskies.mod.forge.mixin.compat.old_create")) {
            return LoadedMods.getOldCreate();
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
