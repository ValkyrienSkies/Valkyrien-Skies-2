package org.valkyrienskies.mod.mixin.feature.entity_collision;

import static org.valkyrienskies.mod.common.util.EntityDragger.backOff;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.world.ShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(Player.class)
public abstract class MixinPlayer implements IEntityDraggingInformationProvider {

    // Allow players to crouch walk on ships
    @WrapMethod(method = "maybeBackOffFromEdge")
    private Vec3 preMaybeBackOffFromEdge(Vec3 vec3, MoverType moverType, Operation<Vec3> original) {
        Vec3 vanillaResult = original.call(vec3, moverType);

        // Only apply ship-edge protection if vanilla decided to apply backoff
        if (vanillaResult.subtract(vec3).lengthSqr() < 0.001) {
            return vanillaResult;
        }

        Player player = (Player) (Object) this;
        Level level = player.level();
        if (level == null) {
            return vanillaResult;
        }

        ShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
        if (shipWorld == null) {
            return vanillaResult;
        }

        Ship ship = null;
        var dragInfo = getDraggingInformation();
        if (dragInfo != null && dragInfo.getLastShipStoodOn() != null) {
            ship = shipWorld.getLoadedShips().getById(dragInfo.getLastShipStoodOn());
        }

        if (ship == null) {
            return vanillaResult;
        }

        return backOff(vec3, ship, player, level);
    }
}
