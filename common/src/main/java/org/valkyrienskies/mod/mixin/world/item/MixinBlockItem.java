package org.valkyrienskies.mod.mixin.world.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.PlayerUtil;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

    @Shadow
    protected abstract @Nullable BlockState getPlacementState(BlockPlaceContext context);

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"
        ),
        method = "place"
    )
    private BlockState transformPlayerWhenPlacingBlock(final BlockItem instance, final BlockPlaceContext context) {
        if (context.getPlayer() != null) {
            return PlayerUtil.transformPlayerTemporarily(context.getPlayer(), context.getLevel(),
                context.getClickedPos(), () -> getPlacementState(context));
        } else {
            return getPlacementState(context);
        }

    }
}
