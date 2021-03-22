package org.valkyrienskies.mod.mixin.block.entity;

import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LootableContainerBlockEntity.class)
public class MixinLootableContainerBlockEntity {

    @Redirect(
        method = "canPlayerUse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;squaredDistanceTo(DDD)D"
        )
    )
    private double includeShipsInDistanceCheck(
        final PlayerEntity receiver, final double x, final double y, final double z) {
        return VSGameUtilsKt.squaredDistanceToInclShips(receiver, x, y, z);
    }

}
