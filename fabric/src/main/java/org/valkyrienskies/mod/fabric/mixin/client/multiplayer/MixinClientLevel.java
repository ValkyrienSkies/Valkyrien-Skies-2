package org.valkyrienskies.mod.fabric.mixin.client.multiplayer;

import java.util.Objects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.ChunkSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.fabric.world.DynamicLightingKt;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @Shadow
    public abstract ChunkSource getChunkSource();

    @Inject(
        at = @At("HEAD"),
        method = "onChunkLoaded"
    )
    public void onChunkLoad(final int i, final int j, final CallbackInfo ci) {
        DynamicLightingKt.getDynamicLightingListener().onChunkLoad(i, j,
            Objects.requireNonNull(this.getChunkSource().getChunkNow(i, j)));
    }
}
