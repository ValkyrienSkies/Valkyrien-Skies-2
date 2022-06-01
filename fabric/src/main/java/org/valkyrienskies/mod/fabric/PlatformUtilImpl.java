package org.valkyrienskies.mod.fabric;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloadListener;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

public class PlatformUtilImpl {

    public static void registerDataResourceManager(final ResourceReloadListener listener, final String name) {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(new IdentifiableResourceReloadListener() {
                public Identifier getFabricId() {
                    return new Identifier(ValkyrienSkiesMod.MOD_ID, name);
                }

                @Override
                public CompletableFuture<Void> reload(final Synchronizer synchronizer,
                    final ResourceManager resourceManager,
                    final Profiler profiler, final Profiler profiler2,
                    final Executor executor, final Executor executor2) {
                    return listener.reload(synchronizer, resourceManager, profiler, profiler2,
                        executor, executor2);
                }
            });
    }

}
