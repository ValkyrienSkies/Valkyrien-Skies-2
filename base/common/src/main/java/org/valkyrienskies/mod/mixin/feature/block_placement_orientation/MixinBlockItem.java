package org.valkyrienskies.mod.mixin.feature.block_placement_orientation;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.mod.common.PlayerUtil;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {
    
    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"
        ),
        method = "place",
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void transformPlayerWhenPlacing(final BlockPlaceContext ignore,
        final CallbackInfoReturnable<InteractionResult> cir, final BlockPlaceContext context) {
        if (context == null || context.getPlayer() == null) {
            return;
        }

        PlayerUtil.transformPlayerTemporarily(context.getPlayer(), context.getLevel(), context.getClickedPos());
    }

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;",
            shift = Shift.AFTER
        ),
        method = "place"
    )
    private void untransformPlayerAfterPlacing(final BlockPlaceContext context,
        final CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getPlayer() == null) {
            return;
        }

        PlayerUtil.untransformPlayer(context.getPlayer());
    }
}
