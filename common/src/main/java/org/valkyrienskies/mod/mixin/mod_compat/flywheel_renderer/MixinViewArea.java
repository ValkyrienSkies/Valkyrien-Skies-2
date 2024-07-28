package org.valkyrienskies.mod.mixin.mod_compat.flywheel_renderer;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.ShipRenderer;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.compat.flywheel.FlywheelCompat;
import org.valkyrienskies.mod.compat.flywheel.ShipEffect;

@Mixin(ViewArea.class)
public class MixinViewArea {

    @Shadow
    @Final
    protected Level level;

    @Shadow
    protected int chunkGridSizeY;

    /**
     * This mixin creates models for the sections for flywheel
     */
    @Inject(method = "setDirty", at = @At("HEAD"), cancellable = true)
    private void preScheduleRebuild(final int x, final int y, final int z,
        final boolean important, final CallbackInfo callbackInfo) {

        final int yIndex = y - level.getMinSection();

        if (yIndex < 0 || yIndex >= chunkGridSizeY) {
            return; // Weird, but just ignore it
        }

        var ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, x, z);
        if (ship == null) return;
        if (ShipRendererKt.getShipRenderer(ship) != ShipRenderer.FLYWHEEL) return;
        if (!FlywheelCompat.INSTANCE.isFlywheelInstalled())
            throw new IllegalStateException("Trying to render with flywheel, but no flywheel installed");

        ShipEffect.Companion.getShipEffect(ship).setDirty(x, y, z, important);
    }

    @Inject(method = "getRenderChunkAt", at = @At("HEAD"), cancellable = true)
    private void preGetRenderedChunk(final BlockPos pos,
        final CallbackInfoReturnable<RenderChunk> callbackInfoReturnable) {
        final int chunkX = Mth.floorDiv(pos.getX(), 16);
        final int chunkY = Mth.floorDiv(pos.getY() - level.getMinBuildHeight(), 16);
        final int chunkZ = Mth.floorDiv(pos.getZ(), 16);

        if (chunkY < 0 || chunkY >= chunkGridSizeY) {
            return; // Weird, but ignore it
        }

        var ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, chunkX, chunkZ);
        if (ship != null && ShipRendererKt.getShipRenderer(ship) == ShipRenderer.FLYWHEEL) {
            callbackInfoReturnable.setReturnValue(null);
        }
    }
}
