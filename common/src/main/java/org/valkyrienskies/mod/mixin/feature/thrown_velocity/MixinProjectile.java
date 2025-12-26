package org.valkyrienskies.mod.mixin.feature.thrown_velocity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(Projectile.class)
public abstract class MixinProjectile extends Entity implements TraceableEntity {

    @Shadow
    public abstract @Nullable Entity getOwner();

    public MixinProjectile(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * If someone shoot a projectile from a ship, add ship velocity to this projectile.
     * This is a bit different with items in that it won't be dragged by the ship,
     * because projectiles need world speed in damage check and other things.
     */
    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    private void preFirstTick(CallbackInfo ci) {
        final Entity owner = getOwner();
        if(!firstTick || owner == null) return;
        Ship ship = VSGameUtilsKt.getShipMountedTo(owner);
        EntityDraggingInformation info = ((IEntityDraggingInformationProvider)owner).getDraggingInformation();
        if (ship == null && info.isEntityBeingDraggedByAShip()) {
            ship = ValkyrienSkies.getShipById(level(), info.getLastShipStoodOn());
        }
        VSGameUtilsKt.applyShipVelocity(this, ship);
    }


}
