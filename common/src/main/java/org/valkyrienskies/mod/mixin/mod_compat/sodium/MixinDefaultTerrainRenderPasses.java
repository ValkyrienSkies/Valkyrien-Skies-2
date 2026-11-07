package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;

@Mixin(DefaultTerrainRenderPasses.class)
public class MixinDefaultTerrainRenderPasses {

    @Shadow
    @Final
    @Mutable
    private static TerrainRenderPass[] ALL;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void onClinit(CallbackInfo ci) {
        TerrainRenderPass[] old = ALL;
        TerrainRenderPass[] updated = new TerrainRenderPass[old.length + 1];

        System.arraycopy(old, 0, updated, 0, old.length);
        updated[old.length] = SodiumCompat.AIR_POCKET_PASS;

        ALL = updated;
    }
}
