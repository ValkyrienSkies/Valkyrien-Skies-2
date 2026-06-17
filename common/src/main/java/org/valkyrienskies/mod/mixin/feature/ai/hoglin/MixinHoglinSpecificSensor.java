package org.valkyrienskies.mod.mixin.feature.ai.hoglin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.sensing.HoglinSpecificSensor;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Mirror of MixinPiglinSpecificSensor: when the world-frame ±8/±4 cube turns up no repellent, also search each nearby ship's local frame.
@Mixin(HoglinSpecificSensor.class)
public abstract class MixinHoglinSpecificSensor {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @ModifyReturnValue(method = "findNearestRepellent", at = @At("RETURN"))
    private Optional<BlockPos> vs$alsoSearchShipFrames(
        final Optional<BlockPos> worldResult,
        final ServerLevel level, final Hoglin hoglin
    ) {
        if (worldResult.isPresent()) return worldResult;
        final double mx = hoglin.getX(), my = hoglin.getY(), mz = hoglin.getZ();
        final AABBd probe = VS$PROBE.get()
            .setMin(mx - 9, my - 5, mz - 9)
            .setMax(mx + 9, my + 5, mz + 9);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
                VS$IN.get().set(mx, my, mz), VS$OUT.get()
            );
            final BlockPos seed = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
            final Optional<BlockPos> shipHit = BlockPos.findClosestMatch(
                seed, 8, 4, p -> level.getBlockState(p).is(BlockTags.HOGLIN_REPELLENTS)
            );
            if (shipHit.isPresent()) return shipHit;
        }
        return worldResult;
    }
}
