package org.valkyrienskies.mod.mixin.feature.entity_collision;

import static org.valkyrienskies.mod.common.util.EntityDragger.backOff;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.world.ShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(Player.class)
public abstract class MixinPlayer implements IEntityDraggingInformationProvider {

    @Shadow
    protected abstract boolean isStayingOnGroundSurface();

    @Shadow
    @Final
    private Abilities abilities;

    // Allow players to crouch walk on ships
    @Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
    private void preMaybeBackOffFromEdge(final Vec3 vec3, final MoverType moverType,
        final CallbackInfoReturnable<Vec3> callbackInfoReturnable) {
        if (getDraggingInformation().isEntityBeingDraggedByAShip() && getDraggingInformation().getLastShipStoodOn() != null && getDraggingInformation().getTicksSinceStoodOnShip() < 1) {
            Player player = (Player) (Object) this;
            Level level = player.level();
            if (level != null ) { // && level.isClientSide) {
                ShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
                if (shipWorld == null) {
                    return;
                }
                Ship ship = shipWorld.getLoadedShips().getById(
                    getDraggingInformation().getLastShipStoodOn());
                if (ship == null) {
                    return;
                }
                if (vec3.y <= 0.0f && (moverType == MoverType.SELF || moverType == MoverType.PLAYER) && this.isStayingOnGroundSurface() && !this.abilities.flying) {
                    Vec3 adjustedVec = backOff(vec3, ship, player, level);

                    callbackInfoReturnable.setReturnValue(adjustedVec);
                }
            }
        }
    }
}
