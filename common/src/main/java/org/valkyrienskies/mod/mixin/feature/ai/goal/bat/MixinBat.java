package org.valkyrienskies.mod.mixin.feature.ai.goal.bat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Bat resting reads cells in the world frame; ship-mounted ceilings live in shipyard frame
// so the world cell at bat.above() is air, the bat wakes every tick, and its setPosRaw Y
// snap lands a fixed integer offset that misses any non-integer ship ceiling.
@Mixin(Bat.class)
public abstract class MixinBat {

    @Unique
    private static final ThreadLocal<Vector3d> VS$VEC_A = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$VEC_B = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @WrapOperation(
        method = "customServerAiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;isRedstoneConductor(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean vs$projectRestingCeiling(
        final BlockState worldState, final BlockGetter blockGetter, final BlockPos worldPosBelow,
        final Operation<Boolean> original
    ) {
        if (original.call(worldState, blockGetter, worldPosBelow)) return true;
        if (!(blockGetter instanceof Level level)) return false;

        final Bat bat = (Bat) (Object) this;
        final BlockPos abovePos = bat.blockPosition().above();
        return vs$findShipCeilingShip(level, bat, abovePos) != null;
    }

    @ModifyArg(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ambient/Bat;setPosRaw(DDD)V"
        ),
        index = 1
    )
    private double vs$snapToShipCeilingY(final double vanillaY) {
        final Bat bat = (Bat) (Object) this;
        final Level level = bat.level();
        if (level == null) return vanillaY;

        final BlockPos abovePos = bat.blockPosition().above();
        if (level.getBlockState(abovePos).isRedstoneConductor(level, abovePos)) {
            return vanillaY;
        }

        final Ship ship = vs$findShipCeilingShip(level, bat, abovePos);
        if (ship == null) return vanillaY;

        // Solve worldToShip(bx, Y, bz).y == ceilingLocalY for Y. The ceiling-bottom plane is
        // tilted in world space for a rotated ship, so a point on that plane at the bat's
        // shipyard xz isn't at the bat's world xz — sample two world Ys and linearly solve.
        final Matrix4dc w2s = ship.getTransform().getWorldToShip();
        final double bx = bat.getX();
        final double by = bat.getY();
        final double bz = bat.getZ();

        final Vector3d in = VS$VEC_A.get();
        final Vector3d out = VS$VEC_B.get();
        final int ceilingLocalY = (int) Math.floor(w2s.transformPosition(
            in.set(abovePos.getX() + 0.5, abovePos.getY() + 0.5, abovePos.getZ() + 0.5), out
        ).y);
        final double sample0Y = w2s.transformPosition(in.set(bx, by, bz), out).y;
        final double sample1Y = w2s.transformPosition(in.set(bx, by + 1.0, bz), out).y;
        final double dy = sample1Y - sample0Y;
        if (Math.abs(dy) < 1.0e-9) return vanillaY;

        return by + (ceilingLocalY - sample0Y) / dy - bat.getBbHeight();
    }

    // The "below the ceiling" filter rejects wall false positives: on a rotated ship, the
    // world above-pos can project into a wall block, which would test as a conductor and
    // attach the bat mid-wall. Real ceilings have an open cell beneath them.
    @Unique
    private static Ship vs$findShipCeilingShip(final Level level, final Bat bat, final BlockPos worldAbovePos) {
        final double bx = bat.getX();
        final double by = bat.getY();
        final double bz = bat.getZ();
        final AABBd probe = VS$PROBE.get()
            .setMin(bx - 0.5, by - 0.5, bz - 0.5)
            .setMax(bx + 0.5, by + 0.5, bz + 0.5);
        final Vector3d aboveWorld = VS$VEC_A.get().set(
            worldAbovePos.getX() + 0.5, worldAbovePos.getY() + 0.5, worldAbovePos.getZ() + 0.5
        );
        final Vector3d local = VS$VEC_B.get();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            ship.getTransform().getWorldToShip().transformPosition(aboveWorld, local);
            final BlockPos localPos = BlockPos.containing(local.x, local.y, local.z);
            if (!level.getBlockState(localPos).isRedstoneConductor(level, localPos)) continue;
            if (level.getBlockState(localPos.below()).blocksMotion()) continue;
            return ship;
        }
        return null;
    }
}
