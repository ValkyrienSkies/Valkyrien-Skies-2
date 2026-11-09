package org.valkyrienskies.mod.mixin.feature.ai.sleep;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Sleep architecture: keep the entity at world coords throughout sleep. setBedOccupied/setSleepingPos stay shipyard (the bed-block lives in shipyard chunks); setPosToBed gets projected to world at sleep start, and baseTick re-projects per tick so the body tracks the ship as it moves — generating ordinary movement that MixinServerEntity ships as PacketEntityShipMotion.
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntitySleep {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @WrapOperation(
        method = "startSleeping",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setPosToBed(Lnet/minecraft/core/BlockPos;)V"
        )
    )
    private void vs$projectSetPosToBed(
        final LivingEntity self,
        final BlockPos bedPos,
        final Operation<Void> original
    ) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(self.level(), bedPos);
        if (ship == null) {
            original.call(self, bedPos);
            return;
        }
        // Project the bed CENTER (not corner) — vanilla's `pos + 0.5` after worldToShip drifts off the rendered bed center on a rotated ship and lands the body in a neighbouring bed's render slot.
        final Vector3d worldCenter = vs$projectBedCenterToWorld(ship, bedPos);
        self.setPos(worldCenter.x, worldCenter.y, worldCenter.z);
    }

    @Inject(method = "baseTick", at = @At("TAIL"))
    private void vs$trackBedAsMount(final CallbackInfo ci) {
        final LivingEntity self = (LivingEntity) (Object) this;
        final Level level = self.level();
        if (level.isClientSide || !self.isSleeping()) return;
        final Optional<BlockPos> sleepingPosOpt = self.getSleepingPos();
        if (sleepingPosOpt.isEmpty()) return;
        final BlockPos sleepingPos = sleepingPosOpt.get();
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, sleepingPos);
        if (ship == null) return;
        final Vector3d worldCenter = vs$projectBedCenterToWorld(ship, sleepingPos);
        if (worldCenter.x == self.getX() && worldCenter.y == self.getY() && worldCenter.z == self.getZ()) return;
        self.setPos(worldCenter.x, worldCenter.y, worldCenter.z);
    }

    @Unique
    private static Vector3d vs$projectBedCenterToWorld(final Ship ship, final BlockPos bedPos) {
        return ship.getTransform().getShipToWorld().transformPosition(
            VS$IN.get().set(bedPos.getX() + 0.5, bedPos.getY() + 0.6875, bedPos.getZ() + 0.5),
            VS$OUT.get()
        );
    }
}
