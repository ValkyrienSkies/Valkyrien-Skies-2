package org.valkyrienskies.mod.mixin.feature.ai.door;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Three ship-compat gaps in InteractWithDoor: world-frame paths overlaying ship doors fail the door-tag check; isDoorTooFarAway and the "hold open" predicate compare shipyard BlockPos against world entity position.
@Mixin(InteractWithDoor.class)
public abstract class MixinInteractWithDoor {

    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @WrapOperation(
        method = {
            "method_46966",
            "closeDoorsThatIHaveOpenedOrPassedThrough"
        },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/pathfinder/Node;asBlockPos()Lnet/minecraft/core/BlockPos;"
        )
    )
    private static BlockPos vs$asBlockPosShipDoorRedirect(
        final Node node, final Operation<BlockPos> original,
        @Local(argsOnly = true) final ServerLevel serverLevel
    ) {
        final BlockPos raw = original.call(node);
        if (vs$isWoodenDoor(serverLevel.getBlockState(raw))) return raw;
        final BlockPos shipLocalDoor = vs$findShipDoorAt(serverLevel, raw);
        return shipLocalDoor != null ? shipLocalDoor : raw;
    }

    private static boolean vs$isWoodenDoor(final BlockState state) {
        return state.is(BlockTags.WOODEN_DOORS);
    }

    private static BlockPos vs$findShipDoorAt(final Level level, final BlockPos worldPos) {
        final Vector3d worldCenter = VS$CENTER.get().set(
            worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5
        );
        final AABBd probe = VS$PROBE.get()
            .setMin(worldPos.getX(), worldPos.getY(), worldPos.getZ())
            .setMax(worldPos.getX() + 1.0, worldPos.getY() + 1.0, worldPos.getZ() + 1.0);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            if (!ship.getWorldAABB().containsPoint(worldCenter)) continue;
            final Matrix4dc worldToShip = ship.getTransform().getWorldToShip();
            final Vector3d local = worldToShip.transformPosition(worldCenter, VS$LOCAL.get());
            final BlockPos shipLocal = BlockPos.containing(local.x, local.y, local.z);
            if (vs$isWoodenDoor(level.getBlockState(shipLocal))) return shipLocal;
        }
        return null;
    }

    @WrapOperation(
        method = "isDoorTooFarAway",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private static boolean vs$isTooFarProjected(
        final BlockPos instance, final Position position, final double dist,
        final Operation<Boolean> original,
        @Local(argsOnly = true) final LivingEntity entity
    ) {
        return vs$closerInWorldFrame(entity.level(), instance, position, dist);
    }

    @WrapOperation(
        method = "method_30765",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private static boolean vs$keepOpenProjected(
        final BlockPos instance, final Position position, final double dist,
        final Operation<Boolean> original,
        @Local(argsOnly = true) final LivingEntity entity
    ) {
        return vs$closerInWorldFrame(entity.level(), instance, position, dist);
    }

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
}
