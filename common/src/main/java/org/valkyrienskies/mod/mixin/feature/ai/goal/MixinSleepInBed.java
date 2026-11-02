package org.valkyrienskies.mod.mixin.feature.ai.goal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Sleep gate logic for ship-mounted beds: vanilla SleepInBed compares the bed pos (HOME memory, shipyard for ship-mounted beds) against the entity's world position. Project bed center to world for the proximity checks (corner-safe via Vec3.atCenterOf + manual distance), and bypass canStillUse's Y/distance gates entirely for ship-bed sleep — they fail unpredictably from dragger tick-race + Y-floor rounding even when the entity is glued to the bed.
@Mixin(SleepInBed.class)
public class MixinSleepInBed {

    @WrapOperation(
        method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean vs$closerStart(
        final BlockPos instance, final Position position, final double dist,
        final Operation<Boolean> original,
        @Local(argsOnly = true) final LivingEntity entity
    ) {
        return vs$closerInWorldFrame(entity.level(), instance, position, dist);
    }

    @WrapOperation(
        method = "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;J)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean vs$closerStill(
        final BlockPos instance, final Position position, final double dist,
        final Operation<Boolean> original,
        @Local(argsOnly = true) final LivingEntity entity
    ) {
        return vs$closerInWorldFrame(entity.level(), instance, position, dist);
    }

    @Unique
    private static boolean vs$closerInWorldFrame(
        final Level level, final BlockPos instance, final Position position, final double dist
    ) {
        final Vec3 cellCenterWorld = VSGameUtilsKt.toWorldCoordinates(level, Vec3.atCenterOf(instance));
        final Vec3 entityWorld = VSGameUtilsKt.toWorldCoordinates(
            level, new Vec3(position.x(), position.y(), position.z()));
        final double dx = cellCenterWorld.x - entityWorld.x;
        final double dy = cellCenterWorld.y - entityWorld.y;
        final double dz = cellCenterWorld.z - entityWorld.z;
        return dx * dx + dy * dy + dz * dz < dist * dist;
    }

    // Wholesale bypass of canStillUse's Y/distance gates for ship-mounted sleep. Vanilla checks: HOME present (KEEP — handle the same way so a villager that lost its home doesn't stay glued), Activity.REST active (KEEP — dawn-wake), then `entity.Y > bedY + 0.4` (SKIP — pitch/roll ships, fractional Y, dragger tick-race, and BlockPos.containing's floor rounding flip this at the boundary), and `closerToCenterThan(bedPos, entity, 1.14)` (SKIP — same tick-race / projection issues). Bed-destruction wake still fires via `LivingEntity.aiStep`'s `checkBedExists`, damage wake via `hurt → stopSleeping`, right-click wake via `MixinBedBlock.kickVillagerOutOfBed`. The previous narrow distance-only bypass left the Y check running and on certain ships its floor-rounding waked the villager every tick.
    @Inject(
        method = "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;J)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void vs$shipBedCanStillUse(
        final ServerLevel level, final LivingEntity entity, final long gameTime,
        final CallbackInfoReturnable<Boolean> cir
    ) {
        if (VSGameUtilsKt.getShipMountedToData(entity, null) == null) return;
        final Brain<?> brain = entity.getBrain();
        if (brain.getMemory(MemoryModuleType.HOME).isEmpty()) {
            cir.setReturnValue(false);
            return;
        }
        if (!brain.isActive(Activity.REST)) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(true);
    }
}
