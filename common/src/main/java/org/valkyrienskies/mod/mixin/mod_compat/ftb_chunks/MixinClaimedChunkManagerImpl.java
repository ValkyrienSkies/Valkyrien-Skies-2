package org.valkyrienskies.mod.mixin.mod_compat.ftb_chunks;

import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(ClaimedChunkManagerImpl.class)
public abstract class MixinClaimedChunkManagerImpl {
    @Unique
    private Entity entity = null;

    @ModifyVariable(method = "shouldPreventInteraction", at = @At("HEAD"), name = "entity", remap = false)
    private Entity ValkyrienSkies$entity(final Entity entity) {
        this.entity = entity;
        return entity;
    }

    @ModifyArg(
        method = "shouldPreventInteraction",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"
        )
    )
    private BlockPos ValkyrienSkies$newChunkDimPos(final BlockPos pos) {
        if (entity == null || !VSGameConfig.SERVER.getFTBChunks().getShipsProtectedByClaims()) {
            return pos;
        }

        final Level level = entity.level();

        final Ship ship = ValkyrienSkies.getShipManagingBlock(level, pos);
        if (ship == null) {
            return pos;
        }

        final Vector3d vec = ship.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(Vec3.atCenterOf(pos)));
        final BlockPos newPos = BlockPos.containing(VectorConversionsMCKt.toMinecraft(vec));

        if ((newPos.getY() > level.getMaxBuildHeight() || newPos.getY() < level.getMinBuildHeight()) &&
            !VSGameConfig.SERVER.getFTBChunks().getShipsProtectionOutOfBuildHeight()) {
            return pos;
        }

        return newPos;
    }
}
