package org.valkyrienskies.mod.mixin.entity.item;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.game.ships.ShipDataCommon;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ItemEntity.class)
public class MixinItemEntity {

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/ItemEntity;setPos(DDD)V"
        ),
        method = "<init>(Lnet/minecraft/world/level/Level;DDD)V"
    )
    private void setPosInWorld(final ItemEntity instance, final double x, final double y, final double z,
        final Level level, final double d, final double e, final double f) {

        final ShipDataCommon ship = VSGameUtilsKt.getShipManagingPos(level, x, y, z);
        if (ship == null) {
            instance.setPos(x, y, z);
        } else {
            final Vector3d posInWorld = ship.getShipToWorld().transformPosition(new Vector3d(x, y, z));
            instance.setPos(posInWorld.x, posInWorld.y, posInWorld.z);
        }
    }

}
