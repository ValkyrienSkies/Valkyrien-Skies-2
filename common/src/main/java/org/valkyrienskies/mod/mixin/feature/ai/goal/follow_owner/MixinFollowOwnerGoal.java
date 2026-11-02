package org.valkyrienskies.mod.mixin.feature.ai.goal.follow_owner;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.ShipAwareCollisionUtil;

// Lets tamed pets (wolves, cats, parrots, etc.) teleport to and land safely on ship-mounted
// owners. Vanilla canTeleportTo's getBlockPathTypeStatic + noCollision both read world-only;
// for an owner on a ship deck the candidate cells are air at world coords and walls/decks
// only exist in shipyard frame.
@Mixin(FollowOwnerGoal.class)
public abstract class MixinFollowOwnerGoal {

    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);
    @Unique
    private static final ThreadLocal<BlockPos.MutableBlockPos> VS$SHIPYARD_POS =
        ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    @WrapOperation(
        method = "canTeleportTo",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/pathfinder/WalkNodeEvaluator;getBlockPathTypeStatic(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos$MutableBlockPos;)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;"
        )
    )
    private BlockPathTypes vs$shipAwarePathType(final BlockGetter blockGetter,
        final BlockPos.MutableBlockPos worldPos, final Operation<BlockPathTypes> original) {
        final BlockPathTypes vanilla = original.call(blockGetter, worldPos);
        if (vanilla == BlockPathTypes.WALKABLE) return vanilla;
        if (!(blockGetter instanceof Level level)) return vanilla;

        final double cx = worldPos.getX() + 0.5;
        final double cy = worldPos.getY() + 0.5;
        final double cz = worldPos.getZ() + 0.5;
        final Vector3d worldCenter = VS$CENTER.get().set(cx, cy, cz);
        final AABBd probe = VS$PROBE.get()
            .setMin(worldPos.getX(), worldPos.getY(), worldPos.getZ())
            .setMax(worldPos.getX() + 1.0, worldPos.getY() + 1.0, worldPos.getZ() + 1.0);
        final Vector3d local = VS$LOCAL.get();
        final BlockPos.MutableBlockPos shipyardPos = VS$SHIPYARD_POS.get();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            if (!ship.getWorldAABB().containsPoint(worldCenter)) continue;
            ship.getTransform().getWorldToShip().transformPosition(worldCenter, local);
            shipyardPos.set((int) Math.floor(local.x), (int) Math.floor(local.y), (int) Math.floor(local.z));
            final BlockPathTypes shipyardTypes = original.call(blockGetter, shipyardPos);
            if (shipyardTypes == BlockPathTypes.WALKABLE) return shipyardTypes;
        }
        return vanilla;
    }

    // Final canTeleportTo step validates the pet's bbox at the candidate via noCollision;
    // vanilla world-only would accept candidates that clip into ship walls/ceilings.
    @WrapOperation(
        method = "canTeleportTo",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"
        )
    )
    private boolean vs$noCollisionIncludingShips(
        final LevelReader levelReader, final Entity entity, final AABB aabb,
        final Operation<Boolean> original
    ) {
        if (!(levelReader instanceof Level level)) return original.call(levelReader, entity, aabb);
        return ShipAwareCollisionUtil.noCollisionIncludingShips(level, entity, aabb);
    }
}
