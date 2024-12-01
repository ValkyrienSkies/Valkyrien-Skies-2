package org.valkyrienskies.mod.mixin.feature.save_mob_location_on_ship;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.entity.ShipyardPosSavable;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ChunkMap.class)
public class MixinChunkMap {

    @Shadow
    @Final
    ServerLevel level;

    /**
     * Save mob's shipyard position when it gets unloaded
     *
     * @author G_Mungus
     */

    @Inject(method = "removeEntity", at = @At("HEAD"))
    protected void unloadEntityMixin(Entity entity, CallbackInfo info) {
        if (entity instanceof Mob mob) {
            Vector3d shipyardPos = valkyrienskies$getShipyardPos(mob);
            if (shipyardPos != null &&
                VSGameUtilsKt.getShipManagingPos(this.level, shipyardPos) != null &&
                ((ShipyardPosSavable)mob).valkyrienskies$getUnloadedShipyardPos() == null) {
                ((ShipyardPosSavable)mob).valkyrienskies$setUnloadedShipyardPos(shipyardPos);
            }
        }
    }

    /**
     * Teleport mob to correct position on ship when loaded back in
     *
     * @author G_Mungus
     */

    @Inject(method = "addEntity", at = @At("RETURN"))
    protected void loadEntityMixin(Entity entity, CallbackInfo info) {
        if (entity instanceof Mob mob) {
            Vector3d shipyardPos = ((ShipyardPosSavable)mob).valkyrienskies$getUnloadedShipyardPos();
            if(shipyardPos != null) {
                if (VSGameConfig.SERVER.getSaveMobsPositionOnShip()){
                    mob.teleportTo(shipyardPos.x, shipyardPos.y, shipyardPos.z);
                }
                ((ShipyardPosSavable) mob).valkyrienskies$setUnloadedShipyardPos(null);
            }
        }
    }


    /**
     * Helper method to get shipyard pos of mob on a ship
     *
     * @author G_Mungus
     */
    @Unique
    private Vector3d valkyrienskies$getShipyardPos(Entity entity) {
        EntityDraggingInformation dragInfo = ((IEntityDraggingInformationProvider) entity).getDraggingInformation();

        if (dragInfo.getLastShipStoodOn() != null) {
            Ship ship = VSGameUtilsKt.getAllShips(this.level).getById(dragInfo.getLastShipStoodOn());
            if (ship != null && ship.getWorldAABB().containsPoint(VectorConversionsMCKt.toJOML(entity.position()))) {
                return ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(entity.position()));
            }
        }

        return null;
    }
}
