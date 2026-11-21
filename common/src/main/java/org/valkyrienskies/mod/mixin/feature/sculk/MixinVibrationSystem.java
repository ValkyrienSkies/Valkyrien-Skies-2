package org.valkyrienskies.mod.mixin.feature.sculk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem.Listener;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Mixin(Listener.class)
public abstract class MixinVibrationSystem {
    @WrapMethod(
            method = "scheduleVibration"
    )
    private void scheduleVibration(
        ServerLevel level, VibrationSystem.Data data, GameEvent gameEvent, GameEvent.Context context, Vec3 pos, Vec3 sensorPos,
        Operation<Void> original
    ) { // GameEvent changed to Holder<GameEvent> in 1.21
        original.call(level, data, gameEvent, context,
            ValkyrienSkies.transformToWorld(level, pos), // this MAY break due to using pos
            ValkyrienSkies.transformToWorld(level, sensorPos)
        );
        /*
        todo: this may break due to differences between the original and this. this is because the original uses BlockPos.containing to get a specific pos instead of inferring it from the given
        original.call(level, data, gameEvent, context,
            VectorConversionsMCKt.toMinecraft(VSGameUtilsKt.getWorldCoordinates(level, BlockPos.containing(pos), VectorConversionsMCKt.toJOML(pos))),
            VectorConversionsMCKt.toMinecraft(VSGameUtilsKt.getWorldCoordinates(level, BlockPos.containing(sensorPos), VectorConversionsMCKt.toJOML(sensorPos)))
        );
        can a dot get on this please
         */
    }

    @WrapMethod(method = "isOccluded")
    private static boolean adjustOcclusionForWorldPosition(Level level, Vec3 pos1, Vec3 pos2, Operation<Boolean> original) {
        Ship ship1 = ValkyrienSkies.getShipManagingPos(level, pos1);
        Ship ship2 = ValkyrienSkies.getShipManagingPos(level, pos2);
        if (ship1 == ship2) {
            // Use vanilla behavior if and only if ships match.
            // Otherwise, we risk traversing millions of blocks from one shipyard to another.
            return original.call(level, pos1, pos2);
        }
        // Transform both positions to world and check occlusion.
        Vec3 pos1InWorld = ValkyrienSkies.transformToWorld(level, pos1);
        Vec3 pos2InWorld = ValkyrienSkies.transformToWorld(level, pos2); // same as above, may break
        if (original.call(level, pos1InWorld, pos2InWorld)) {
            return true;
        }
        // For both ships, check occlusion as if the event has fired on the same ship.
        boolean result = false;
        if (ship2 != null) {
            Vec3 pos1InShip2 = ValkyrienSkies.transformToShip(ship2, pos1InWorld);
            result |= original.call(level, pos1InShip2, pos2);
        }
        if (ship1 != null) {
            Vec3 pos2InShip1 = ValkyrienSkies.transformToShip(ship1, pos2InWorld);
            result |= original.call(level, pos1, pos2InShip1);
        }
        return result;
    }
}
