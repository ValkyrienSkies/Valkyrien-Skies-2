package org.valkyrienskies.mod.mixin.feature.ai.frog;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Lay frogspawn on ship-mounted ponds — vanilla TryLaySpawnOnWaterNearLand is world-only and never finds water around a ship-mounted pregnant frog.
@Mixin(Frog.class)
public abstract class MixinFrogShipFrogspawn {

    @Unique
    private static final ThreadLocal<Vector3d> VS$WORLD = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void vs$tryLayOnShipFrogspawn(final CallbackInfo ci) {
        final Frog frog = (Frog) (Object) this;
        final Level level = frog.level();

        if (!frog.getBrain().hasMemoryValue(MemoryModuleType.IS_PREGNANT)) return;
        if (frog.isInWater() || !frog.onGround()) return;

        final Ship ship = VSGameUtilsKt.getEnclosingShip(frog);
        if (ship == null) return;

        final Vector3d world = VS$WORLD.get().set(frog.getX(), frog.getY(), frog.getZ());
        final Vector3d local = ship.getTransform().getWorldToShip().transformPosition(world, VS$LOCAL.get());
        final BlockPos shipyardBelow = BlockPos.containing(local.x, local.y, local.z).below();

        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            final BlockPos shipyardWater = shipyardBelow.relative(direction);
            if (!level.getBlockState(shipyardWater)
                .getCollisionShape(level, shipyardWater)
                .getFaceShape(Direction.UP)
                .isEmpty()) continue;
            if (!level.getFluidState(shipyardWater).is(Fluids.WATER)) continue;

            final BlockPos shipyardSpawnAbove = shipyardWater.above();
            if (!level.getBlockState(shipyardSpawnAbove).isAir()) continue;

            final BlockState frogspawnState = Blocks.FROGSPAWN.defaultBlockState();
            level.setBlock(shipyardSpawnAbove, frogspawnState, 3);
            level.gameEvent(
                GameEvent.BLOCK_PLACE, shipyardSpawnAbove,
                GameEvent.Context.of(frog, frogspawnState)
            );
            level.playSound(
                null, frog,
                SoundEvents.FROG_LAY_SPAWN, SoundSource.BLOCKS,
                1.0F, 1.0F
            );
            frog.getBrain().eraseMemory(MemoryModuleType.IS_PREGNANT);
            return;
        }
    }
}
