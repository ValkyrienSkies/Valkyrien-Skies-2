package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
        world.scheduleTick(pos, this, 2);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void injectTick(
        final BlockState state,
        final ServerLevel world,
        final BlockPos pos,
        final RandomSource random,
        final CallbackInfo ci
    ) {
        if (!world.getBlockTicks().hasScheduledTick(pos, this)) {
            world.scheduleTick(pos, this, 2);
        }
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
        if (!isContact(blockState)) {
            return false;
        }
        final Direction targetDir = blockState.getValue(FACING);
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

        if (!(world instanceof Level)) return;

        final Level level = (Level) (world);
        final BlockPos detectPos = pos.relative(direction);
        final BlockState facingState = world.getBlockState(detectPos);
        if (isContact(facingState)) {
            cir.setReturnValue(facingState.getValue(FACING) == direction.getOpposite());
            return;
        }
        if (world.getBlockState(pos).is(AllBlocks.ELEVATOR_CONTACT.get())) {
            // DO NOT RAY CAST ELEVATOR CONTACT
            // BECAUSE IT IS
            // BASED ON ELEVATOR'S
            // TARGET POSITION
            // NOT THE
            // PEER CONTACT'S POSITION
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
            final Vec3 checkPos = new Vec3(checkPoint.x, checkPoint.y, checkPoint.z);
            for (final AbstractContraptionEntity contraption : world.getEntitiesOfClass(AbstractContraptionEntity.class, searchAABB)) {
                final Vec3 localPos = contraption.toLocalVector(checkPos, 1);
                final StructureBlockInfo info = contraption.getContraption().getBlocks().get(BlockPos.containing(localPos));
                if (info == null) {
                    continue;
                }
                if (!isContact(info.state())) {
                    continue;
                }
                final Direction dir = info.state().getValue(FACING);
                final Vec3 checkVec = contraption.toGlobalVector(localPos.relative(dir, 0.65), 1);
                final Vector3d checkP = new Vector3d(checkVec.x, checkVec.y, checkVec.z);
                if (ship != null) {
                    ship.getWorldToShip().transformPosition(checkP);
                }
                if (pos.equals(BlockPos.containing(checkP.x, checkP.y, checkP.z))) {
                    foundBlock = null;
                    found = true;
                    break;
                }
            }
            if (found) {
                break;
            }
        }
        if (!found) {
            final Vector3d foundPos = new Vector3d();
            for (final Ship targetShip : VSGameUtilsKt.getShipsIntersecting(level, searchAABB)) {
                for (final Vector3d checkPoint : checkPoints) {
                    targetShip.getWorldToShip().transformPosition(checkPoint, foundPos);
                    if (targetShip != ship) {
                        foundBlock = BlockPos.containing(foundPos.x, foundPos.y, foundPos.z);
                        if (hasContact(world, pos, direction, ship, foundBlock, targetShip)) {
                            found = true;
                            break;
                        }
                    }
                    final Vec3 checkPos = new Vec3(foundPos.x, foundPos.y, foundPos.z);
                    final AABB searchAABB2 = VectorConversionsMCKt.toMinecraft(
                        VectorConversionsMCKt.toJOML(searchAABB).transform(targetShip.getWorldToShip())
                    );
                    for (final AbstractContraptionEntity contraption : world.getEntitiesOfClass(AbstractContraptionEntity.class, searchAABB2)) {
                        final Vec3 localPos = contraption.toLocalVector(checkPos, 1);
                        final StructureBlockInfo info = contraption.getContraption().getBlocks().get(BlockPos.containing(localPos));
                        if (info == null) {
                            continue;
                        }
                        if (!isContact(info.state())) {
                            continue;
                        }
                        final Direction dir = info.state().getValue(FACING);
                        final Vec3 checkVec = contraption.toGlobalVector(localPos.relative(dir, 0.65), 1);
                        final Vector3d checkP = new Vector3d(checkVec.x, checkVec.y, checkVec.z);
                        if (targetShip != ship) {
                            targetShip.getShipToWorld().transformPosition(checkP);
                            if (ship != null) {
                                ship.getWorldToShip().transformPosition(checkP);
                            }
                        }
                        if (pos.equals(BlockPos.containing(checkP.x, checkP.y, checkP.z))) {
                            foundBlock = null;
                            found = true;
                            break;
                        }
                    }
                    if (found) {
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
        if (foundBlock != null) {
            final BlockState targetState = world.getBlockState(foundBlock);
            if (!targetState.getValue(POWERED)) {
                level.setBlockAndUpdate(foundBlock, targetState.setValue(POWERED, true));
            }
        }
        cir.setReturnValue(true);
    }

    @Unique
    private static boolean isContact(final BlockState state) {
        return state.is(AllBlocks.REDSTONE_CONTACT.get()) || state.is(AllBlocks.ELEVATOR_CONTACT.get());
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
