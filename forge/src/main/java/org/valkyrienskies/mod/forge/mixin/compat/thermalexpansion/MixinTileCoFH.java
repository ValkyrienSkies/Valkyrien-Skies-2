package org.valkyrienskies.mod.forge.mixin.compat.thermalexpansion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(targets = "cofh.core.tileentity.TileCoFH", remap = false)
@Pseudo
public abstract class MixinTileCoFH extends BlockEntity {

    // Necessary constructor because we are extending [BlockEntity]
    public MixinTileCoFH(final BlockEntityType<?> arg) {
        super(arg);
    }

    @Inject(
        at = @At("HEAD"),
        method = "playerWithinDistance",
        cancellable = true
    )
    private void prePlayerWithinDistance(final Player player, final double distanceSq,
        final CallbackInfoReturnable<Boolean> ci) {
        final Level level = getLevel();
        // Sanity check
        if (level == null) {
            return;
        }
        final BlockPos worldPos = getBlockPos();
        final double squareDistance =
            VSGameUtilsKt.squaredDistanceToInclShips(player, worldPos.getX(), worldPos.getY(), worldPos.getZ());
        final boolean isPlayerWithinDistance = !isRemoved() && squareDistance <= distanceSq;
        ci.setReturnValue(isPlayerWithinDistance);
    }
}
