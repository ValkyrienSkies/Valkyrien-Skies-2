package org.valkyrienskies.mod.mixin.feature.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Sleep gate logic for ship-mounted beds: vanilla SleepInBed compares the bed pos (HOME memory, shipyard for ship-mounted beds) against the entity's world position. The proximity checks are handled by ShipAwareDistance; bypass canStillUse's Y/distance gates entirely for ship-bed sleep — they fail unpredictably from dragger tick-race + Y-floor rounding even when the entity is glued to the bed.
@Mixin(SleepInBed.class)
public class MixinSleepInBed {

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
