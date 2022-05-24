package org.valkyrienskies.mod.mixin.server;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.pipelines.VSPipeline;
import org.valkyrienskies.physics_api_krunch.KrunchBootstrap;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

    @Inject(
        method = "runServer",
        at = @At("HEAD")
    )
    private void preRunServer(final CallbackInfo ci) {
        KrunchBootstrap.INSTANCE.loadNativeBinaries();
        VSPipeline.Companion.createVSPipeline();
    }

    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    private void preTick(final CallbackInfo ci) {
        VSPipeline.Companion.getVSPipeline().preTickGame();
    }

    @Inject(
        method = "tick",
        at = @At("TAIL")
    )
    private void postTick(final CallbackInfo ci) {
        VSPipeline.Companion.getVSPipeline().postTickGame();
    }

    @Inject(
        method = "shutdown",
        at = @At("HEAD")
    )
    private void preShutdown(final CallbackInfo ci) {
        VSPipeline.Companion.deleteVSPipeline();
    }
}
