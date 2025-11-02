package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.contact.RedstoneContactBlock;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.TickPriority;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(RedstoneContactBlock.class)
public abstract class MixinRedstoneContactBlock extends WrenchableDirectionalBlock {

    @Shadow
    @Final
    public static BooleanProperty POWERED;
    @Unique
    private static final double CHECK_BOUND = 2.0 / 16;
    @Unique
    private static final double INTERSECT_BOUND = CHECK_BOUND + 0.1;
    @Unique
    private static final double MAX_ALIGNMENT_ANGLE = -(1 - Math.cos(Math.toRadians(20)));

    protected MixinRedstoneContactBlock() {
        super(null);
    }

    @Override
    public void onPlace(
        final BlockState state,
        final Level world,
        final BlockPos pos,
        final BlockState oldState,
        final boolean isMoving
    ) {
        super.onPlace(state, world, pos, oldState, isMoving);
        world.scheduleTick(pos, AllBlocks.REDSTONE_CONTACT.get(), 2, TickPriority.NORMAL);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE_ASSIGN", shift = At.Shift.BY, by = 2, target = "Lcom/simibubi/create/content/redstone/contact/RedstoneContactBlock;hasValidContact(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void injectTick(
        final BlockState state,
        final ServerLevel world,
        final BlockPos pos,
        final RandomSource random,
        final CallbackInfo ci,
        final boolean hasValidContact
    ) {
        world.scheduleTick(pos, AllBlocks.REDSTONE_CONTACT.get(), 2, TickPriority.NORMAL);
    }

    @Unique
    private static boolean hasContact(
        final LevelAccessor world,
        final BlockPos selfPos,
        final Direction selfDir,
        final Ship ship,
        final BlockPos targetPos,
        final Ship targetShip
    ) {
        final BlockState blockState = world.getBlockState(targetPos);
        if (!AllBlocks.REDSTONE_CONTACT.has(blockState)) {
            return false;
        }
        final Direction targetDir = blockState.getValue(FACING);
        final Vector3d selfDirection = new Vector3d(selfDir.getStepX(), selfDir.getStepY(), selfDir.getStepZ());
        final Vector3d targetDirection = new Vector3d(targetDir.getStepX(), targetDir.getStepY(), targetDir.getStepZ());
        if (ship != null) {
            ship.getShipToWorld().transformDirection(selfDirection);
        }
        if (targetShip != null) {
            targetShip.getShipToWorld().transformDirection(targetDirection);
        }
        final double angle = selfDirection.angleCos(targetDirection);
        if (angle > MAX_ALIGNMENT_ANGLE) {
            return false;
        }
        final Vector3d[] checkPoints = makeCheckPoints(targetPos.relative(targetDir).getCenter(), targetDir);
        if (targetShip != null) {
            final Matrix4dc shipMat = targetShip.getShipToWorld();
            for (final Vector3d checkPoint : checkPoints) {
                shipMat.transformPosition(checkPoint);
            }
        }
        if (ship != null) {
            final Matrix4dc shipMat = ship.getWorldToShip();
            for (final Vector3d checkPoint : checkPoints) {
                shipMat.transformPosition(checkPoint);
            }
        }
        for (final Vector3d checkPoint : checkPoints) {
            if (selfPos.equals(BlockPos.containing(checkPoint.x, checkPoint.y, checkPoint.z))) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "hasValidContact", at = @At("RETURN"), cancellable = true)
    private static void injectHasValidContact(
        final LevelAccessor world,
        final BlockPos pos,
        final Direction direction,
        final CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValueZ()) {
            return;
        }
        final Level level = (Level) (world);
        final BlockPos detectPos = pos.relative(direction);
        final BlockState blockState = world.getBlockState(detectPos);
        if (AllBlocks.REDSTONE_CONTACT.has(blockState)) {
            cir.setReturnValue(blockState.getValue(FACING) == direction.getOpposite());
            return;
        }
        final Vec3 point = detectPos.getCenter();
        final Vector3d[] checkPoints = makeCheckPoints(point, direction);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship != null) {
            final Matrix4dc shipMat = ship.getShipToWorld();
            for (final Vector3d checkPoint : checkPoints) {
                shipMat.transformPosition(checkPoint);
            }
        }
        final AABB searchAABB = VSGameUtilsKt.transformAabbToWorld(level, new AABB(
            point.x - INTERSECT_BOUND, point.y - INTERSECT_BOUND, point.z - INTERSECT_BOUND,
            point.x + INTERSECT_BOUND, point.y + INTERSECT_BOUND, point.z + INTERSECT_BOUND
        ));
        BlockPos foundBlock = null;
        boolean found = false;

        for (final Vector3d checkPoint : checkPoints) {
            foundBlock = BlockPos.containing(checkPoint.x, checkPoint.y, checkPoint.z);
            if (hasContact(world, pos, direction, ship, foundBlock, null)) {
                found = true;
                break;
            }
        }
        if (!found) {
            final Vector3d foundPos = new Vector3d();
            for (final Ship targetShip : VSGameUtilsKt.getShipsIntersecting(level, searchAABB)) {
                if (targetShip == ship) {
                    continue;
                }
                for (final Vector3d checkPoint : checkPoints) {
                    targetShip.getWorldToShip().transformPosition(checkPoint, foundPos);
                    foundBlock = BlockPos.containing(foundPos.x, foundPos.y, foundPos.z);
                    if (hasContact(world, pos, direction, ship, foundBlock, targetShip)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }
        }
        if (!found) {
            return;
        }
        final BlockState targetState = world.getBlockState(foundBlock);
        if (!targetState.getValue(POWERED)) {
            level.setBlockAndUpdate(foundBlock, targetState.setValue(POWERED, true));
        }
        cir.setReturnValue(true);
    }

    @Unique
    private static Vector3d[] makeCheckPoints(final Vec3 point, final Direction direction) {
        return switch (direction.getAxis()) {
            case X -> new Vector3d[]{
                new Vector3d(point.x, point.y - CHECK_BOUND, point.z - CHECK_BOUND),
                new Vector3d(point.x, point.y - CHECK_BOUND, point.z + CHECK_BOUND),
                new Vector3d(point.x, point.y + CHECK_BOUND, point.z - CHECK_BOUND),
                new Vector3d(point.x, point.y + CHECK_BOUND, point.z + CHECK_BOUND)
            };
            case Y -> new Vector3d[]{
                new Vector3d(point.x - CHECK_BOUND, point.y, point.z - CHECK_BOUND),
                new Vector3d(point.x - CHECK_BOUND, point.y, point.z + CHECK_BOUND),
                new Vector3d(point.x + CHECK_BOUND, point.y, point.z - CHECK_BOUND),
                new Vector3d(point.x + CHECK_BOUND, point.y, point.z + CHECK_BOUND)
            };
            case Z -> new Vector3d[]{
                new Vector3d(point.x - CHECK_BOUND, point.y - CHECK_BOUND, point.z),
                new Vector3d(point.x - CHECK_BOUND, point.y + CHECK_BOUND, point.z),
                new Vector3d(point.x + CHECK_BOUND, point.y - CHECK_BOUND, point.z),
                new Vector3d(point.x + CHECK_BOUND, point.y + CHECK_BOUND, point.z)
            };
        };
    }
}
