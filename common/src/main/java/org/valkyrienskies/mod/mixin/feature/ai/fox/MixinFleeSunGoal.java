package org.valkyrienskies.mod.mixin.feature.ai.fox;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Lets sun-fleeing mobs (skeletons, drowned) find hide cells inside ship interiors.
@Mixin(FleeSunGoal.class)
public abstract class MixinFleeSunGoal {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @Shadow
    @Final
    protected PathfinderMob mob;

    @ModifyReturnValue(method = "getHidePos", at = @At("RETURN"))
    private Vec3 vs$alsoSampleShipFrames(final Vec3 worldFrameResult) {
        if (worldFrameResult != null) return worldFrameResult;
        final Level level = mob.level();
        if (level == null) return null;
        final RandomSource random = mob.getRandom();
        final double mx = mob.getX(), my = mob.getY(), mz = mob.getZ();
        final AABBd probe = VS$PROBE.get()
            .setMin(mx - 16, my - 16, mz - 16)
            .setMax(mx + 16, my + 16, mz + 16);
        final Vector3d in = VS$IN.get();
        final Vector3d out = VS$OUT.get();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            ship.getTransform().getWorldToShip().transformPosition(in.set(mx, my, mz), out);
            final BlockPos shipLocalSeed = BlockPos.containing(out.x, out.y, out.z);
            for (int i = 0; i < 10; i++) {
                final BlockPos shipLocalCandidate = shipLocalSeed.offset(
                    random.nextInt(20) - 10,
                    random.nextInt(6) - 3,
                    random.nextInt(20) - 10
                );
                ship.getTransform().getShipToWorld().transformPosition(in.set(
                    shipLocalCandidate.getX() + 0.5,
                    shipLocalCandidate.getY() + 0.5,
                    shipLocalCandidate.getZ() + 0.5
                ), out);
                final BlockPos worldCandidate = BlockPos.containing(out.x, out.y, out.z);
                if (level.canSeeSky(worldCandidate)) continue;
                if (mob.getWalkTargetValue(worldCandidate) >= 0.0f) continue;
                return Vec3.atBottomCenterOf(worldCandidate);
            }
        }
        return null;
    }
}
