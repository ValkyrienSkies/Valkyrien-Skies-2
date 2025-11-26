package org.valkyrienskies.mod.mixin.mod_compat.create.item;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.trains.track.TrackBlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.common.PlayerUtil;

@Mixin(TrackBlockItem.class)
public class MixinTrackBlockItem {
    @WrapMethod(
        method = "getPlacementState"
    )
    private BlockState wrapPlacementState(UseOnContext context, Operation<BlockState> original){
        if(context != null && context.getPlayer() != null) {
            PlayerUtil.transformPlayerTemporarily(context.getPlayer(), context.getLevel(), context.getClickedPos());
            BlockState state = original.call(context);
            PlayerUtil.untransformPlayer(context.getPlayer());
            return state;
        } else return original.call(context);
    }
}
