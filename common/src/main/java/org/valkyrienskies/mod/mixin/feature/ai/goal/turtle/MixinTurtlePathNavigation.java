package org.valkyrienskies.mod.mixin.feature.ai.goal.turtle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// TurtlePathNavigation.isStableDestination reads getBlockState directly via the navigation's stored Level (bypassing PathNavigationRegion); for ship-mounted water tanks or decks the world cells are air, both branches (water-when-going-home, solid-floor-otherwise) reject the candidate. Project the world cell to overlapping ships when vanilla returns air. Same shape as MixinStriderPathNavigation.
@Mixin(targets = "net.minecraft.world.entity.animal.Turtle$TurtlePathNavigation")
public abstract class MixinTurtlePathNavigation {

    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @WrapOperation(
        method = "isStableDestination",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$shipAwareGetBlockState(final Level level, final BlockPos worldPos,
        final Operation<BlockState> original) {
        final BlockState vanilla = original.call(level, worldPos);
        if (!vanilla.isAir()) return vanilla;

        final Vector3d worldCenter = VS$CENTER.get().set(
            worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);
        final AABBd probe = VS$PROBE.get()
            .setMin(worldPos.getX(), worldPos.getY(), worldPos.getZ())
            .setMax(worldPos.getX() + 1.0, worldPos.getY() + 1.0, worldPos.getZ() + 1.0);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            if (!ship.getWorldAABB().containsPoint(worldCenter)) continue;
            final Vector3d local = ship.getTransform().getWorldToShip().transformPosition(
                worldCenter, VS$LOCAL.get()
            );
            final BlockPos shipyardPos = BlockPos.containing(local.x, local.y, local.z);
            final BlockState shipState = level.getBlockState(shipyardPos);
            if (!shipState.isAir()) return shipState;
        }
        return vanilla;
    }
}
