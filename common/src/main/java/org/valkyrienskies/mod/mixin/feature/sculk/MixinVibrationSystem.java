package org.valkyrienskies.mod.mixin.feature.sculk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem.Listener;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

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
            VectorConversionsMCKt.toMinecraft(VSGameUtilsKt.getWorldCoordinates(level, BlockPos.containing(pos), VectorConversionsMCKt.toJOML(pos))),
            VectorConversionsMCKt.toMinecraft(VSGameUtilsKt.getWorldCoordinates(level, BlockPos.containing(sensorPos), VectorConversionsMCKt.toJOML(sensorPos)))
        );
    }

    @WrapMethod(method = "isOccluded")
    private static boolean adjustOcclusionForWorldPosition(Level level, Vec3 pos1, Vec3 pos2, Operation<Boolean> original) {
        Ship ship1 = VSGameUtilsKt.getShipManagingPos(level, pos1);
        Ship ship2 = VSGameUtilsKt.getShipManagingPos(level, pos2);
        if (ship1 == ship2) {
            // Use vanilla behavior if and only if ships match.
            // Otherwise, we risk traversing millions of blocks from one shipyard to another.
            return original.call(level, pos1, pos2);
        }
        // Transform both positions to world and check occlusion.
        Vec3 pos1InWorld = VectorConversionsMCKt.toMinecraft(VSGameUtilsKt.getWorldCoordinates(level, BlockPos.containing(pos1), VectorConversionsMCKt.toJOML(pos1)));
        Vec3 pos2InWorld = VectorConversionsMCKt.toMinecraft(VSGameUtilsKt.getWorldCoordinates(level, BlockPos.containing(pos2), VectorConversionsMCKt.toJOML(pos2)));
        if (original.call(level, pos1InWorld, pos2InWorld)) {
            return true;
        }
        // For both ships, check occlusion as if the event has fired on the same ship.
        boolean result = false;
        if (ship2 != null) {
            Vec3 pos1InShip2 = VectorConversionsMCKt.toMinecraft(ship2.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(pos1InWorld)));
            result |= original.call(level, pos1InShip2, pos2);
        }
        if (ship1 != null) {
            Vec3 pos2InShip1 = VectorConversionsMCKt.toMinecraft(ship1.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(pos2InWorld)));
            result |= original.call(level, pos1, pos2InShip1);
        }
        return result;
    }
}
