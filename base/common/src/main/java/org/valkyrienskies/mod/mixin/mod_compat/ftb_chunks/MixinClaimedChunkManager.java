package org.valkyrienskies.mod.mixin.mod_compat.ftb_chunks;

import dev.ftb.mods.ftbchunks.data.ClaimedChunkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(ClaimedChunkManager.class)
public abstract class MixinClaimedChunkManager {
    @Unique
    private Entity entity = null;

    @ModifyVariable(method = "protect", at = @At("HEAD"), name = "entity", remap = false)
    private Entity ValkyrienSkies$entity(final Entity entity) {
        this.entity = entity;
        return entity;
    }

    @ModifyArg(
        method = "protect",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"
        )
    )
    private BlockPos ValkyrienSkies$newChunkDimPos(final BlockPos pos) {
        if (entity == null || !VSGameConfig.SERVER.getFTBChunks().getShipsProtectedByClaims()) {
            return pos;
        }

        final Level level = entity.level;

        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) {
            return pos;
        }

        final Vector3d vec = ship.getShipToWorld().transformPosition(new Vector3d(pos.getX(), pos.getY(), pos.getZ()));
        final BlockPos newPos = new BlockPos(VectorConversionsMCKt.toMinecraft(vec));

        if (
            (newPos.getY() > level.getMaxBuildHeight() || newPos.getY() < level.getMinBuildHeight()) &&
                !VSGameConfig.SERVER.getFTBChunks().getShipsProtectionOutOfBuildHeight()
        ) {
            return pos;
        }

        return newPos;
    }
}
