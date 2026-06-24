package org.valkyrienskies.mod.mixin.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("rawtypes")
@Mixin(LayerLightSectionStorage.class)
public abstract class MixinLayerLightSectionStorage {

    @Shadow
    @Final
    protected Long2ObjectMap queuedSections;

    @Inject(method = "createDataLayer", at = @At("HEAD"), cancellable = true)
    private void vs$copyQueuedDataLayer(final long sectionPos, final CallbackInfoReturnable<DataLayer> cir) {
        final DataLayer queued = (DataLayer) this.queuedSections.get(sectionPos);
        if (queued != null) {
            cir.setReturnValue(queued.copy());
        }
    }
}
