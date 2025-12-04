package org.valkyrienskies.mod.mixin.feature.bed_fix;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Player.class)
public abstract class MixinPlayer {
    /**
     * @author Bunting_chj
     * @reason prevent respawning to shipyard when ship is deleted
     */
    @Inject(
        method = "findRespawnPositionAndUseSpawnBlock",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void cancelIfInShipyard(ServerLevel serverLevel, BlockPos blockPos, float f, boolean bl,
        boolean bl2, CallbackInfoReturnable<Optional<Vec3>> cir){
        if (cir.getReturnValue().isEmpty()) return;
        Vec3 pos = cir.getReturnValue().get();
        if (VSGameUtilsKt.isBlockInShipyard(serverLevel, pos) && VSGameUtilsKt.getShipManagingPos(serverLevel, pos) == null) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
