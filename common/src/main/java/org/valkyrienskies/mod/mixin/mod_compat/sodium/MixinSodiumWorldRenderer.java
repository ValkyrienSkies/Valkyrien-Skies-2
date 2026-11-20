package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.SortedSet;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import net.minecraft.client.Minecraft;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.render.ShipRenderViews;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.render.NativeShipPieces;
import org.valkyrienskies.mod.common.render.batched.ShipPieceBuffers;
import org.valkyrienskies.mod.common.assembly.SeamlessChunksManager;
import org.valkyrienskies.mod.common.render.batched.ShipBatchRenderer;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.LoadedMods.FlywheelVersion;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.RenderSectionManagerDuck;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.SodiumWorldRendererDuck;

@Mixin(SodiumWorldRenderer.class)
public abstract class MixinSodiumWorldRenderer implements SodiumWorldRendererDuck {

    @Shadow
    private ClientLevel world;

    @Shadow
    private RenderSectionManager renderSectionManager;
    @Unique
    private SortedRenderLists currentRenderLists;
    @Unique
    private boolean vs$prevFrameHadShips;

    @Override
    public void vs$renderNestedShipView(final Runnable draw) {
        final SortedRenderLists outer = currentRenderLists;
        currentRenderLists = null;
        try {
            if (renderSectionManager instanceof RenderSectionManagerDuck duck) duck.vs$renderNestedShipView(draw);
            else draw.run();
        } finally {
            currentRenderLists = outer;
        }
    }

    @Override
    public void vs$markShipRenderListsDirty() {
        ((RenderSectionManagerDuck) this.renderSectionManager).vs$markShipRenderListsDirty();
    }

    @Override
    public void vs$invalidateShipSectionCache(final ClientShip ship) {
        ((RenderSectionManagerDuck) this.renderSectionManager).vs$invalidateShipSectionCache(ship);
    }

    @Redirect(
        method = "renderBlockEntity",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V")
    )
    private static void renderShipBlockEntityInShipyard(
        final PoseStack instance,
        final double x, final double y, final double z,
        final PoseStack ignore,
        final RenderBuffers bufferBuilders,
        final Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions,
        final float tickDelta,
        final MultiBufferSource.BufferSource immediate,
        final double camX, final double camY, final double camZ,
        final BlockEntityRenderDispatcher dispatcher,
        final BlockEntity entity
    ) {
        final BlockPos pos = entity.getBlockPos();

        // We don't use the level from the BlockEntityRenderDispatcher because it might not be a ClientLevel
        // see https://github.com/maruohon/litematica/issues/937 for more info
        final ClientShip ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(Minecraft.getInstance().level, pos);

        if (ship == null) {
            instance.translate(x, y, z);
        } else {
            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), instance, pos, camX, camY, camZ);
        }
    }

    @WrapOperation(method = "renderBlockEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private static void vs$renderPieceBlockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntity entity,
        float tick, PoseStack pose, MultiBufferSource source, Operation<Void> original,
        @Local(argsOnly = true, ordinal = 0) double x, @Local(argsOnly = true, ordinal = 1) double y,
        @Local(argsOnly = true, ordinal = 2) double z) {
        final var level = Minecraft.getInstance().level;
        if (level != null && !VSGameUtilsKt.getAllShips(level).getSplitShipIds().isEmpty()) {
            final var ship = VSGameUtilsKt.getLoadedShipManagingPos(level, entity.getBlockPos());
            if (ship != null) {
                final var pieces = NativeShipPieces.pieces(level, ship.getId());
                if (!pieces.isEmpty()) {
                    pose.popPose();
                    pose.pushPose();
                    if (ShipRenderViews.shouldRenderShip(ship)) {
                        ShipPieceBuffers.draw(pieces, entity.getBlockPos(), pose, new Vec3(x, y, z), source,
                            (piecePose, clipped) -> original.call(dispatcher, entity, tick, piecePose, clipped));
                    }
                    return;
                }
            }
        }
        original.call(dispatcher, entity, tick, pose, source);
    }

    @Shadow
    protected abstract void renderBlockEntities(PoseStack matrices, RenderBuffers bufferBuilders,
        Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions, float tickDelta,
        MultiBufferSource.BufferSource immediate, double x, double y, double z, BlockEntityRenderDispatcher blockEntityRenderer);

    @Redirect(
        method = "renderBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderBuffers;Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;Lnet/minecraft/client/Camera;F)V",
        at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/SodiumWorldRenderer;renderBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderBuffers;Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;FLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDDLnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;)V")
    )
    public void renderShipBlockEntities(final SodiumWorldRenderer instance, final PoseStack matrices,
        final RenderBuffers bufferBuilders,
        final Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions, final float tickDelta,
        final MultiBufferSource.BufferSource immediate, final double x, final double y, final double z,
        final BlockEntityRenderDispatcher blockEntityRenderer) {

        renderBlockEntities(matrices, bufferBuilders, blockBreakingProgressions, tickDelta, immediate, x, y, z, blockEntityRenderer);

        try {
            for (final var entry : ((RenderSectionManagerDuck) this.renderSectionManager).vs_getShipRenderLists().entrySet()) {
                if (!ShipRenderViews.shouldRenderShip(entry.getKey())) continue;
                this.currentRenderLists = entry.getValue();
                renderBlockEntities(matrices, bufferBuilders, blockBreakingProgressions, tickDelta, immediate, x, y, z, blockEntityRenderer);
            }
        } finally {
            this.currentRenderLists = null;
        }
    }
    
    @ModifyExpressionValue(
        method = "renderBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderBuffers;Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;FLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDDLnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;)V",
        at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/RenderSectionManager;getRenderLists()Lme/jellysquid/mods/sodium/client/render/chunk/lists/SortedRenderLists;")
    )
    private SortedRenderLists redirectGetRenderLists(final SortedRenderLists original) {
        if (currentRenderLists != null) {
            return currentRenderLists;
        } else {
            return original;
        }
    }

    @Inject(
        method = "renderBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderBuffers;Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;Lnet/minecraft/client/Camera;F)V",
        at = @At("TAIL")
    )
    private void vs$renderBatchedShipBlockEntities(final PoseStack matrices, final RenderBuffers bufferBuilders,
        final Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions, final Camera camera,
        final float tickDelta, final CallbackInfo ci) {
        ShipBatchRenderer.INSTANCE.renderBlockEntities(world, matrices, bufferBuilders.bufferSource(), camera,
            tickDelta);
    }

    @Inject(method = "setupTerrain", at = @At("HEAD"))
    private void preUpdateChunks(final Camera camera, final Viewport viewport, final int frame,
        final boolean spectator, final boolean updateChunksImmediately, final CallbackInfo callbackInfo) {
        final boolean curFrameHasShips =
            !VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance()).getLoadedShips().isEmpty();
        if (vs$prevFrameHadShips != curFrameHasShips) {
            this.vs$markShipRenderListsDirty();
        }
        vs$prevFrameHadShips = curFrameHasShips;
    }

    @Inject(method = "setupTerrain", at = @At("TAIL"))
    private void updateShipRenderLists(final Camera camera, final Viewport viewport, final int frame,
        final boolean spectator, final boolean updateChunksImmediately, final CallbackInfo ci) {
        ((RenderSectionManagerDuck) this.renderSectionManager).vs$updateShipRenderLists(camera, viewport, frame,
            spectator);
    }

    /**
     * Fix entities in ships not rendering when Sodium is installed
     */
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void isEntityVisible(final Entity entity, final CallbackInfoReturnable<Boolean> cir) {
        if (VSGameUtilsKt.isBlockInShipyard(world, entity.position())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * TODO: check if this comment is true for sodium renderer.
     * Process deferred ship chunk packets BEFORE vanilla's light updates so that
     * ship chunks are loaded and their light is computed before render chunks compile.
     */
    @Inject(
        method = "setupTerrain",
        at = @At("HEAD")
    )
    private void drainShipChunksBeforeLightUpdate(final Camera camera, final Viewport viewport, final int frame, final boolean spectator, final boolean updateChunksImmediately, final CallbackInfo ci) {
        final SeamlessChunksManager manager = SeamlessChunksManager.get();
        if (manager != null) {
            if (LoadedMods.getFlywheel() == FlywheelVersion.V1) {
                return;
            }
            manager.drainDeferredBatch();
            // Drain all queued light updates so the light engine has the latest data
            if (!world.isLightUpdateQueueEmpty()) {
                world.pollLightUpdates();
            }
        }
    }

    @Inject(
        method = {"reload", "unloadWorld"},
        at = @At("HEAD"),
        remap = false
    )
    private void deleteStorages(CallbackInfo ci) {
        ShipBatchRenderer.INSTANCE.freeAll();
        SodiumCompat.deleteStorages();
    }

    @Inject(
        method = "initRenderer",
        at = @At("HEAD"),
        remap = false
    )
    private void populateStorage(CommandList commandList, CallbackInfo ci){
        SodiumCompat.populateWorldFromShipsForFrame(world);
        SodiumCompat.populateLightSectionStorage(world);
        SodiumCompat.populateBiomeSectionStorage(world);

    }
}
