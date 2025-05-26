package org.valkyrienskies.mod.forge.mixin.neoforged.neoforge.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ClientHooks.class)
public class MixinClientHooks {
    @Inject(method = "isBlockEntityRendererVisible", at = @At("HEAD"), cancellable = true, remap = false)
    private static void preIsBlockEntityRendererVisible(
        BlockEntityRenderDispatcher dispatcher,
        BlockEntity blockEntity,
        Frustum frustum,
        CallbackInfoReturnable<Boolean> cir
    ) {
        final BlockEntityRenderer<BlockEntity> renderer = dispatcher.getRenderer(blockEntity);
        if (renderer == null) return;
        final Ship ship = VSGameUtilsKt.getShipManagingPos(blockEntity.getLevel(), blockEntity.getBlockPos());
        if (ship == null) return;
        final AABB renderAABB = renderer.getRenderBoundingBox(blockEntity);
        final AABB transformed = VSGameUtilsKt.transformRenderAABBToWorld((ClientShip) ship, renderAABB);
        if (frustum.isVisible(transformed)) {
            cir.setReturnValue(true);
        }
    }
}
