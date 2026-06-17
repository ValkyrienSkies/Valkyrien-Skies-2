package org.valkyrienskies.mod.mixin.feature.ai.raid;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Raid.updateRaiders kicks any raider whose center.distSqr(raider.blockPosition()) >= 12544 (112 blocks); for raids triggered at a ship-mounted village `center` is shipyard while raiders spawn at world coords (Raid.findRandomSpawnPos uses WORLD_SURFACE), so distance is millions, every raider gets kicked first tick, all subsequent waves spawn back-to-back, raid ends without progress. Project center to world for the comparison. (Raid.tick's isVillage(center) check has the same shipyard-vs-world shape — follow-up if raids still misbehave.)
@Mixin(Raid.class)
public abstract class MixinRaid {

    @WrapOperation(
        method = "updateRaiders",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"
        )
    )
    private double vs$projectCenterForRaiderDistance(
        final BlockPos instance, final Vec3i other,
        final Operation<Double> original
    ) {
        final Raid self = (Raid) (Object) this;
        // Project both cell centers (corner-safe) so the result matches vanilla's center-to-center distance up to a consistent half-block shift.
        final Vec3 worldCenter = VSGameUtilsKt.toWorldCoordinates(self.getLevel(), Vec3.atCenterOf(instance));
        final double dx = worldCenter.x - (other.getX() + 0.5);
        final double dy = worldCenter.y - (other.getY() + 0.5);
        final double dz = worldCenter.z - (other.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }
}
