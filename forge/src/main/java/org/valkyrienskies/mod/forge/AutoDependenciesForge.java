package org.valkyrienskies.mod.forge;

import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import org.valkyrienskies.dependency_downloader.ValkyrienDependencyDownloader;

public class AutoDependenciesForge {
    public static void runUpdater() {
        final boolean isServer = FMLEnvironment.dist.isDedicatedServer();
        if (!isServer) {
            System.setProperty("java.awt.headless", "false");
        }
        ValkyrienDependencyDownloader.start(
            FMLPaths.MODSDIR.get(),
            FMLLoader.getLoadingModList().getModFileById("valkyrienskies").getFile().getFilePath(),
            isServer
        );
    }
}
