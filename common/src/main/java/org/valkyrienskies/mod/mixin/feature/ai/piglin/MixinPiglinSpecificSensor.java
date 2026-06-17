package org.valkyrienskies.mod.mixin.feature.ai.piglin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.PiglinSpecificSensor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Mirror of MixinHoglinSpecificSensor: when the world-frame ±8/±4 cube turns up no piglin repellent, also search each nearby ship's local frame.
@Mixin(PiglinSpecificSensor.class)
public abstract class MixinPiglinSpecificSensor {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @ModifyReturnValue(method = "findNearestRepellent", at = @At("RETURN"))
    private static Optional<BlockPos> vs$alsoSearchShipFrames(
        final Optional<BlockPos> worldResult,
        final ServerLevel level, final LivingEntity entity
    ) {
        if (worldResult.isPresent()) return worldResult;
        final double mx = entity.getX(), my = entity.getY(), mz = entity.getZ();
        final AABBd probe = VS$PROBE.get()
            .setMin(mx - 9, my - 5, mz - 9)
            .setMax(mx + 9, my + 5, mz + 9);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
                VS$IN.get().set(mx, my, mz), VS$OUT.get()
            );
            final BlockPos seed = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
            final Optional<BlockPos> shipHit = BlockPos.findClosestMatch(
                seed, 8, 4, p -> vs$isValidRepellent(level, p)
            );
            if (shipHit.isPresent()) return shipHit;
        }
        return worldResult;
    }

    // Mirror of vanilla's private static isValidRepellent — re-implemented (rather than @Invoker'd) since vanilla's check is short.
    @Unique
    private static boolean vs$isValidRepellent(final ServerLevel level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        final boolean repellent = state.is(BlockTags.PIGLIN_REPELLENTS);
        if (repellent && state.is(Blocks.SOUL_CAMPFIRE)) {
            return CampfireBlock.isLitCampfire(state);
        }
        return repellent;
    }
}
