package org.valkyrienskies.mod.mixin.mod_compat.immersive_portals;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixinducks.client.render.LevelRendererVanillaDuck;
import qouteall.imm_ptl.core.render.MyBuiltChunkStorage;
import qouteall.imm_ptl.core.render.VisibleSectionDiscovery;

/**
 * Calls vs$addShipVisibleChunks, since immersive portals injects and cancels a callback preventing
 * MixinLevelRendererVanilla from calling it at the right time.
 */
@Mixin(VisibleSectionDiscovery.class)
public class MixinVisibleSectionDiscovery {

    @Inject(
        method = "discoverVisibleSections",
        at = @At("RETURN")
    )
    private static void onDiscoverVisibleSections(ClientLevel world, MyBuiltChunkStorage builtChunks_, Camera camera,
        Frustum frustum, ObjectArrayList<RenderChunkInfo> resultHolder_, CallbackInfo ci) {

        if (!(Minecraft.getInstance().levelRenderer instanceof final LevelRendererVanillaDuck renderer)) return;

        renderer.vs$addShipVisibleChunks(frustum);

    }
}
