package org.valkyrienskies.mod.mixin.feature.ai.wither;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

// LivingEntity.createWitherRose places a Wither Rose at blockPosition() — for a mob killed on a ship deck the world cell is air, canSurvive's pos.below() soil check sees world air, and the rose drops as an item instead of placing.
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityCreateWitherRose {

    @WrapOperation(
        method = "createWitherRose",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$projectRosePosToShipDeck(
        final LivingEntity self, final Operation<BlockPos> original
    ) {
        final BlockPos worldPos = original.call(self);
        // Dragger attribution (the ship the mob is physically standing on) — bare getShipsIntersecting would also match an overhead ship for a mob on the world ground.
        if (!(self instanceof IEntityDraggingInformationProvider provider)) return worldPos;
        final Long shipId = provider.getDraggingInformation().getLastShipStoodOn();
        if (shipId == null) return worldPos;
        final LoadedShip ship =
            VSGameUtilsKt.getShipObjectWorld(self.level()).getLoadedShips().getById(shipId);
        if (ship == null) return worldPos;
        // Project the cell-center (not the mob's exact pos) so FP drift in worldToShip doesn't push y to a cell boundary and floor into the deck block instead of the air cell above it.
        final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
            new Vector3d(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5),
            new Vector3d()
        );
        return BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
    }
}
