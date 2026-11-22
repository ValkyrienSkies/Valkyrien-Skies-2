package org.valkyrienskies.mod.mixin.feature.ai.door;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// InteractWithDoor: world-frame paths overlaying ship doors fail the door-tag check, so resolve the ship door under the node.
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
}
