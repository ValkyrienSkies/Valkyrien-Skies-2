package org.valkyrienskies.mod.mixin.feature.world_weather;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LightningBolt;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(LightningBolt.class)
public abstract class MixinLightningBolt {

    private Ship vs2$draggedShip() {
        final LightningBolt self = (LightningBolt) (Object) this;
        final var info = ((IEntityDraggingInformationProvider) (Object) self).getDraggingInformation();
        final Long shipId = info.getLastShipStoodOn();
        if (!info.isEntityBeingDraggedByAShip() || shipId == null) return null;
        return VSGameUtilsKt.getAllShips(self.level()).getById(shipId);
    }

    private BlockPos vs2$worldBlockToShipyard(BlockPos world, Ship ship) {
        final Vector3d s = ship.getWorldToShip().transformPosition(
            new Vector3d(world.getX() + 0.5, world.getY() + 0.5, world.getZ() + 0.5));
        return BlockPos.containing(s.x, s.y, s.z);
    }

    @ModifyReturnValue(method = "getStrikePosition", at = @At("RETURN"))
    private BlockPos vs2$strikePosInShipyard(BlockPos original) {
        final Ship ship = vs2$draggedShip();
        if (ship == null) return original;
        return vs2$worldBlockToShipyard(((LightningBolt) (Object) this).blockPosition().below(), ship);
    }

    @WrapOperation(method = "spawnFire", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LightningBolt;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos vs2$firePosInShipyard(LightningBolt self, Operation<BlockPos> original) {
        final BlockPos world = original.call(self);
        final Ship ship = vs2$draggedShip();
        return ship == null ? world : vs2$worldBlockToShipyard(world, ship);
    }
}
