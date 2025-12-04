package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.trains.track.TrackPlacement;
import com.simibubi.create.content.trains.track.TrackPlacement.PlacementInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.PlayerUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(TrackPlacement.class)
public abstract class MixinTrackPlacement {

    @WrapMethod(
        method = "tryConnect"
    )
    private static PlacementInfo wrapTryConnect(Level level, Player player, BlockPos pos2, BlockState state2, ItemStack stack,
        boolean girder, boolean maximiseTurn, Operation<PlacementInfo> original) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos2);
        if(ship != null) {
            PlayerUtil.transformPlayerTemporarily(player, level, pos2);
            PlacementInfo info = original.call(level, player, pos2, state2, stack, girder, maximiseTurn);
            PlayerUtil.untransformPlayer(player);
            return info;
        } else return original.call(level, player, pos2, state2, stack, girder, maximiseTurn);
    }
}
