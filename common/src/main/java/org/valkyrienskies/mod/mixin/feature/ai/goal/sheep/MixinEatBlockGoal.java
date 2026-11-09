package org.valkyrienskies.mod.mixin.feature.ai.goal.sheep;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// EatBlockGoal looks for grass at mob.blockPosition() and below; for a sheep on a
// ship-mounted grass block both world cells are air and the goal never engages. Wrap
// the blockPosition reads in canUse/tick to return the projected shipyard cell so the
// downstream getBlockState / destroyBlock / setBlock land on the actual ship grass.
@Mixin(EatBlockGoal.class)
public abstract class MixinEatBlockGoal {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @WrapOperation(
        method = {"canUse", "tick"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$shipyardBlockPos(final Mob mob, final Operation<BlockPos> original) {
        final BlockPos worldPos = original.call(mob);
        final Level level = mob.level();
        if (level == null) return worldPos;
        final Ship ship = VSGameUtilsKt.getEnclosingShip(mob);
        if (ship == null) return worldPos;
        final Vector3d shipyard = ship.getTransform().getWorldToShip().transformPosition(
            VS$IN.get().set(mob.getX(), mob.getY(), mob.getZ()), VS$OUT.get()
        );
        return BlockPos.containing(shipyard.x, shipyard.y, shipyard.z);
    }
}
