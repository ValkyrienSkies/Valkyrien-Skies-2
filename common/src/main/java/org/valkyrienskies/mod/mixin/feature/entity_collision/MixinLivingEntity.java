package org.valkyrienskies.mod.mixin.feature.entity_collision;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.EntityLerper;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    public MixinLivingEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * @author Tomato
     * @reason Adjusted lerping for entities being dragged by ships.
     */
    @Inject(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;aiStep()V", shift = At.Shift.BEFORE)
    )
    private void preAiStep(CallbackInfo ci) {
        // fake lerp movement gaming
        if (this.level != null && this.level.isClientSide() && !firstTick) {
            if (this.isControlledByLocalInstance() || ((Entity) this instanceof LocalPlayer)) return;
            EntityDraggingInformation dragInfo = ((IEntityDraggingInformationProvider) this).getDraggingInformation();
            if (dragInfo != null && dragInfo.getLastShipStoodOn() != null) {
                final ClientShip ship = VSGameUtilsKt.getShipObjectWorld((ClientLevel) level).getAllShips().getById(dragInfo.getLastShipStoodOn());
                if (ship != null) {
                    EntityLerper.INSTANCE.lerpStep(dragInfo, ship, (LivingEntity) (Object) this);
                    EntityLerper.INSTANCE.lerpHeadStep(dragInfo, ship, (LivingEntity) (Object) this);
                }
            }
        }
    }
}
