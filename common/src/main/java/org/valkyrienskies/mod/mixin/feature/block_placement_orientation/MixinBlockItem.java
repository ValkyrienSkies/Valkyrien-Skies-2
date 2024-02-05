package org.valkyrienskies.mod.mixin.feature.block_placement_orientation;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.PlayerUtil;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"
        ),
        method = "place"
    )
    private BlockState transformPlayerWhenPlacing(
        final BlockItem _instance, final BlockPlaceContext _ctx,
        final Operation<BlockState> original, final BlockPlaceContext ctx
    ) {
        if (ctx == null || ctx.getPlayer() == null) {
            return null;
        }

        return PlayerUtil.transformPlayerTemporarily(
            ctx.getPlayer(),
            ctx.getLevel(),
            ctx.getClickedPos(),
            () -> original.call(this, ctx)
        );
    }
}
