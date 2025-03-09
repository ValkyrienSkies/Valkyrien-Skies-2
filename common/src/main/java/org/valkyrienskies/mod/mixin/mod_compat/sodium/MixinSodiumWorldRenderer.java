package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.SortedSet;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.RenderSectionManagerDuck;

@Mixin(SodiumWorldRenderer.class)
public abstract class MixinSodiumWorldRenderer {

    @Shadow
    private ClientLevel level;

    @Shadow
    private RenderSectionManager renderSectionManager;
    @Unique
    private SortedRenderLists currentRenderLists;

    @Redirect(
        method = "renderBlockEntity",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V")
    )
    private static void renderShipBlockEntityInShipyard(final PoseStack instance, final double d, final double e,
        final double f, final PoseStack ignore, final RenderBuffers bufferBuilders,
        final Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions,
        final float tickDelta, final MultiBufferSource.BufferSource immediate, final double camX, final double camY,
        final double camZ, final BlockEntityRenderDispatcher dispatcher, final BlockEntity entity) {

        final BlockPos pos = entity.getBlockPos();

        // fix for https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/818
        if(!(dispatcher.level instanceof ClientLevel)) return;

        final ClientLevel level = (ClientLevel) dispatcher.level;
        final ClientShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, pos);

        if (ship == null) {
            instance.translate(d, e, f);
        } else {
            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), instance, pos, camX, camY, camZ);
        }
    }

    @Shadow
    protected abstract void renderBlockEntities(PoseStack matrices, RenderBuffers bufferBuilders, Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions, float tickDelta, MultiBufferSource.BufferSource immediate, double x, double y, double z, BlockEntityRenderDispatcher blockEntityRenderer, LocalPlayer player, LocalBooleanRef isGlowing);

    @Redirect(
        method = "renderBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderBuffers;Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;Lnet/minecraft/client/Camera;FLcom/llamalad7/mixinextras/sugar/ref/LocalBooleanRef;)V",
        at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/SodiumWorldRenderer;renderBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderBuffers;Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;FLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDDLnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;Lnet/minecraft/client/player/LocalPlayer;Lcom/llamalad7/mixinextras/sugar/ref/LocalBooleanRef;)V")
    )
    public void renderShipBlockEntities(SodiumWorldRenderer instance, PoseStack matrices,
        RenderBuffers bufferBuilders, Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions,
        float tickDelta, MultiBufferSource.BufferSource immediate, double x, double y, double z,
        BlockEntityRenderDispatcher blockEntityRenderer, LocalPlayer player, LocalBooleanRef isGlowing) {

        renderBlockEntities(matrices, bufferBuilders, blockBreakingProgressions, tickDelta, immediate, x, y, z, blockEntityRenderer, player, isGlowing);

        for (final SortedRenderLists renderLists : ((RenderSectionManagerDuck) this.renderSectionManager).vs_getShipRenderLists().values()) {
            this.currentRenderLists = renderLists;
            renderBlockEntities(matrices, bufferBuilders, blockBreakingProgressions, tickDelta, immediate, x, y, z, blockEntityRenderer, player, isGlowing);
        }

        this.currentRenderLists = null;
    }

    @ModifyExpressionValue(
        method = "renderBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderBuffers;Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;FLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDDLnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;Lnet/minecraft/client/player/LocalPlayer;Lcom/llamalad7/mixinextras/sugar/ref/LocalBooleanRef;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;getRenderLists()Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/SortedRenderLists;")
    )
    private SortedRenderLists redirectGetRenderLists(final SortedRenderLists original) {
        if (currentRenderLists != null) {
            return currentRenderLists;
        } else {
            return original;
        }
    }

//    /**
//     * @reason Fix ship ghosts when ships are deleted and camera hasn't moved, and ships not rendering when teleported
//     * and camera hasn't moved
//     */
//    @Inject(method = "updateChunks", at = @At("HEAD"))
//    private void preUpdateChunks(final CallbackInfo callbackInfo) {
//        final boolean curFrameHasShips =
//            !VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance()).getLoadedShips().isEmpty();
//        // Mark the graph dirty if ships were loaded this frame or the previous one
//        if (vs$prevFrameHadShips || curFrameHasShips) {
//            this.renderSectionManager.markGraphDirty();
//        }
//        vs$prevFrameHadShips = curFrameHasShips;
//    }

    /**
     * Fix entities in ships not rendering when Sodium is installed
     */
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void isEntityVisible(final Entity entity, final CallbackInfoReturnable<Boolean> cir) {
        if (VSGameUtilsKt.isBlockInShipyard(level, entity.position())) {
            cir.setReturnValue(true);
        }
    }
}
