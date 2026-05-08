package org.valkyrienskies.mod.fabric.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.valkyrienskies.mod.compat.LoadedMods;

public class ValkyrienSkiesFabricMixinPlugin implements IMixinConfigPlugin {
    private static final String FABRIC_SHIPYARD_ENTITY_MANAGER_MIXIN =
        "org.valkyrienskies.mod.fabric.mixin.feature.shipyard_entities.MixinPersistentEntitySectionManager";
    private static final String C2ME_CHUNK_SYSTEM_ENTITY_MANAGER_MIXIN =
        "com.ishland.c2me.rewrites.chunksystem.mixin.fixes.MixinServerEntityManager";

    private static boolean classExists(final String className) {
        try {
            Class.forName(className, false, ValkyrienSkiesFabricMixinPlugin.class.getClassLoader());
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

        // C2ME rewrites PersistentEntitySectionManager.processUnloads() and removes the vanilla LongSet.removeIf
        // call that the Fabric-only VS patch targets. The common VS mixin already replaces processUnloads()
        // safely, so skip only this redundant Fabric patch when C2ME's chunk-system rewrite is present.
        if (FABRIC_SHIPYARD_ENTITY_MANAGER_MIXIN.equals(mixinClassName)
            && classExists(C2ME_CHUNK_SYSTEM_ENTITY_MANAGER_MIXIN)) {
            return false;
        }

        if (mixinClassName.contains("org.valkyrienskies.mod.fabric.mixin.compat.old_create")) {
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
