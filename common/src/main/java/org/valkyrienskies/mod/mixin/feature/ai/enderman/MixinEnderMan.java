package org.valkyrienskies.mod.mixin.feature.ai.enderman;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Ship-aware enderman patches:
//   - vs$readBlockStateForTeleport: teleport(DDD) walk-down sees ship decks as solid landings.
//   - vs$canSeeSkyIncludingShips: daylight gate considers ship hulls overhead as shelter.
//   - vs$retryTeleportPerNearbyShip: indirect-damage hurt() retries once per nearby ship.
//   - vs$daylightEscapeSampleShipFrames: daylight escape projects vanilla's ±32 random
//     sample into each nearby ship's local frame so ship interiors are reachable.
@Mixin(EnderMan.class)
public abstract class MixinEnderMan {

    // Hot-path scratches: vs$shipBlockAt is invoked per cell of the teleport(DDD) walk-down,
    // which itself runs from up to 64 teleport() attempts on direct damage. Keep ThreadLocals
    // ONLY here — the daylight-escape and retry-per-ship wraps call into teleport(DDD)
    // recursively, so any ThreadLocal they shared with vs$shipBlockAt would be clobbered
    // mid-iteration. Those wraps are rare enough (per-tick or per-damage-event) that fresh
    // allocations are fine.
    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @Invoker("teleport")
    abstract boolean vs$invokeTeleport(double x, double y, double z);

    // teleport(DDD) walk-down validates the landing cell via blocksMotion(); without this
    // wrap the world cell at a ship deck reads air and the enderman teleports through.
    @WrapOperation(
        method = "teleport(DDD)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readBlockStateForTeleport(
        final Level instance, final BlockPos pos, final Operation<BlockState> original
    ) {
        final BlockState worldState = original.call(instance, pos);
        if (worldState.blocksMotion()) return worldState;
        final BlockState shipState = vs$shipBlockAt(instance, pos);
        return shipState != null ? shipState : worldState;
    }

    // canSeeSky only consults the world heightmap, so an enderman under a ship hull thinks
    // it's in open daylight. Short-circuit when world is already occluded; otherwise check
    // for any motion-blocking block above the entity in the ship-included heightmap.
    @WrapOperation(
        method = "customServerAiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;canSeeSky(Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean vs$canSeeSkyIncludingShips(
        final Level instance, final BlockPos pos, final Operation<Boolean> original
    ) {
        if (!original.call(instance, pos)) return false;
        final BlockPos heightInclShips =
            CompatUtil.INSTANCE.getWorldHeightmapPosIncludingShips(instance, Heightmap.Types.MOTION_BLOCKING, pos);
        // pos.Y + 1 (not pos.Y) because float error around standing-on-deck can push
        // blockPos.Y one cell below the deck top.
        return pos.getY() + 1 >= heightInclShips.getY();
    }

    // Indirect-damage path: vanilla makes a single teleport() attempt and frequently lands
    // back in the same hazardous cell (water/fire) when ships are nearby and dry deck cells
    // exist but the random sample missed them. Add one retry per nearby ship.
    //
    // The LivingEntity gate is critical, NOT defensive: vanilla's direct-damage branch runs
    // a `for (i = 0; i < 64; ++i) teleport()` loop, and our wrap fires inside each iteration.
    // Without this gate we'd compound to 64 * (1 + extra) attempts per damage event.
    @WrapOperation(
        method = "hurt",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/EnderMan;teleport()Z"
        )
    )
    private boolean vs$retryTeleportPerNearbyShip(
        final EnderMan self, final Operation<Boolean> original,
        @Local(argsOnly = true) final DamageSource source
    ) {
        if (original.call(self)) return true;
        if (source.getEntity() instanceof LivingEntity) return false;
        final int extra = vs$countNearbyShips(self, 32.0);
        for (int i = 0; i < extra; i++) {
            if (original.call(self)) return true;
        }
        return false;
    }

    // Vanilla daylight escape draws a uniform ±32 random target in WORLD coords. For an
    // enderman on a ship in open ocean that cube is mostly sky/water — the ship's interior
    // occupies almost none of the candidate volume, so vanilla rarely picks a cell inside
    // the ship. After the world-frame attempt fails, project the same ±32 sampling into
    // each nearby ship's local frame so the ship's structure is uniformly represented.
    @WrapOperation(
        method = "customServerAiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/EnderMan;teleport()Z"
        )
    )
    private boolean vs$daylightEscapeSampleShipFrames(
        final EnderMan self, final Operation<Boolean> original
    ) {
        if (original.call(self)) return true;
        final Level level = self.level();
        final double cx = self.getX(), cy = self.getY(), cz = self.getZ();
        final AABBd probe = new AABBd(cx - 32, cy - 32, cz - 32, cx + 32, cy + 32, cz + 32);
        final RandomSource random = self.getRandom();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            final Vector3d shipLocalSelf = ship.getTransform().getWorldToShip()
                .transformPosition(new Vector3d(cx, cy, cz), new Vector3d());
            final double tx = shipLocalSelf.x + (random.nextDouble() - 0.5) * 64.0;
            final double ty = shipLocalSelf.y + (random.nextInt(64) - 32);
            final double tz = shipLocalSelf.z + (random.nextDouble() - 0.5) * 64.0;
            final Vector3d worldTarget = ship.getTransform().getShipToWorld()
                .transformPosition(new Vector3d(tx, ty, tz), new Vector3d());
            if (vs$invokeTeleport(worldTarget.x, worldTarget.y, worldTarget.z)) return true;
        }
        return false;
    }

    @Unique
    private static int vs$countNearbyShips(final EnderMan self, final double radius) {
        final double cx = self.getX();
        final double cy = self.getY();
        final double cz = self.getZ();
        final AABBd probe = new AABBd(
            cx - radius, cy - radius, cz - radius,
            cx + radius, cy + radius, cz + radius
        );
        int count = 0;
        for (final Ship ignored : VSGameUtilsKt.getShipsIntersecting(self.level(), probe)) {
            count++;
        }
        return count;
    }

    @Unique
    private static BlockState vs$shipBlockAt(final Level level, final BlockPos worldPos) {
        final double cx = worldPos.getX() + 0.5;
        final double cy = worldPos.getY() + 0.5;
        final double cz = worldPos.getZ() + 0.5;
        final Vector3d worldCenter = VS$CENTER.get().set(cx, cy, cz);
        final AABBd probe = VS$PROBE.get()
            .setMin(cx - 0.5, cy - 0.5, cz - 0.5)
            .setMax(cx + 0.5, cy + 0.5, cz + 0.5);
        final Vector3d shipLocal = VS$LOCAL.get();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            if (!ship.getWorldAABB().containsPoint(worldCenter)) continue;
            ship.getTransform().getWorldToShip().transformPosition(worldCenter, shipLocal);
            final BlockPos shipLocalPos = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
            final BlockState shipState = level.getBlockState(shipLocalPos);
            if (shipState.blocksMotion()) return shipState;
        }
        return null;
    }
}
