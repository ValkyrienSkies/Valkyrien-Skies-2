package org.valkyrienskies.mod.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.valkyrienskies.dependency_downloader.ValkyrienDependencyDownloader;
import org.valkyrienskies.dependency_downloader.matchers.DependencyMatchResult;

public class AutoDependenciesFabric {

    public static void runUpdater() {
        System.setProperty("java.awt.headless", "false");
        ValkyrienDependencyDownloader.start(
            FabricLoader.getInstance().getGameDir().resolve("mods"),
            // remove any dependencies that are already loaded by fabric
            dep -> FabricLoader.getInstance().getAllMods()
                .stream()
                .noneMatch(loadedMod ->
                    dep.getMatcher().matches(loadedMod.getRoot().getFileSystem()) == DependencyMatchResult.FULFILLED
                ),
            FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER
        );
    }

}
