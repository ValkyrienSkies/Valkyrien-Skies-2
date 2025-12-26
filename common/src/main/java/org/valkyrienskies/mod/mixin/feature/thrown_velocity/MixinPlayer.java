package org.valkyrienskies.mod.mixin.feature.thrown_velocity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(Player.class)
public abstract class MixinPlayer extends LivingEntity {
    protected MixinPlayer(EntityType<? extends LivingEntity> entityType,
        Level level) {
        super(entityType, level);
    }

    @Inject(
        method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
        at = @At("RETURN")
    )
    private void shipVelocityToThrownItem(ItemStack itemStack, boolean bl, boolean bl2,
        CallbackInfoReturnable<ItemEntity> cir) {
        ItemEntity result = cir.getReturnValue();
        if(result == null) return;
        Ship ship = VSGameUtilsKt.getShipMountedTo(this);
        EntityDraggingInformation info = ((IEntityDraggingInformationProvider)this).getDraggingInformation();
        if (ship == null && info.isEntityBeingDraggedByAShip()) {
            ship = ValkyrienSkies.getShipById(level(), info.getLastShipStoodOn());
        }
        ((IEntityDraggingInformationProvider)result).vs$dragImmediately(ship);
    }
}
