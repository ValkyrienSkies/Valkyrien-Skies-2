package org.valkyrienskies.mod.quilt;

import net.fabricmc.api.EnvType;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.minecraft.MinecraftQuiltLoader;
import org.valkyrienskies.dependency_downloader.ValkyrienDependencyDownloader;
import org.valkyrienskies.dependency_downloader.matchers.DependencyMatchResult;

public class AutoDependenciesQuilt {

    public static void runUpdater() {
        final boolean isServer = MinecraftQuiltLoader.getEnvironmentType() == EnvType.SERVER;

        ValkyrienDependencyDownloader.start(
            QuiltLoader.getGameDir().resolve("mods"),
            // remove any dependencies that are already loaded by fabric
            dep -> QuiltLoader.getAllMods()
                .stream()
                .noneMatch(loadedMod ->
                    dep.getMatcher().matches(loadedMod.rootPath().getFileSystem()) == DependencyMatchResult.FULFILLED
                ),
            isServer
        );
    }

}
