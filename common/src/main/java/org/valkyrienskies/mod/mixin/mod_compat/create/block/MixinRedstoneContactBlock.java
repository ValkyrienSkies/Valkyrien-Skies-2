package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;

import com.mojang.datafixers.util.Pair;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.contact.RedstoneContactBlock;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
    private static final double MAX_ALIGNMENT_ANGLE = -(1 - Math.cos(Math.toRadians(20)));

    @Unique
    private static final Map<Pair<Level, BlockPos>, BlockPos> CONTACT_CACHE = new HashMap<>();

    public MixinRedstoneContactBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(final BlockState state, final Level world, final BlockPos pos, final BlockState oldState, final boolean isMoving) {
        if (!VSGameUtilsKt.isBlockInShipyard(world, pos)) {
            return;
        }
        world.scheduleTick(pos, AllBlocks.REDSTONE_CONTACT.get(), 2, TickPriority.NORMAL);
    }

    @Inject(method = "onRemove", at = @At("HEAD"))
    private void injectOnRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving, CallbackInfo ci) {
        if (state.getBlock() == this && newState.isAir()) {
            final BlockPos peerPos = CONTACT_CACHE.remove(Pair.of(world, pos));
            if (peerPos != null && state.getValue(POWERED)) {
                world.scheduleTick(peerPos, AllBlocks.REDSTONE_CONTACT.get(), 2, TickPriority.NORMAL);
            }
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE_ASSIGN", shift = At.Shift.BY, by = 2, target = "Lcom/simibubi/create/content/redstone/contact/RedstoneContactBlock;hasValidContact(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void injectTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random, CallbackInfo ci, boolean hasValidContact) {
        if (!VSGameUtilsKt.isBlockInShipyard(world, pos)) {
            return;
        }
        final BlockPos peerPos = CONTACT_CACHE.remove(Pair.of(world, pos));
        if (peerPos != null && !hasValidContact && state.getValue(POWERED)) {
            world.scheduleTick(peerPos, AllBlocks.REDSTONE_CONTACT.get(), 2, TickPriority.NORMAL);
        }
        world.scheduleTick(pos, AllBlocks.REDSTONE_CONTACT.get(), 2, TickPriority.NORMAL);
    }

    @Unique
    private static boolean hasContact(LevelAccessor world, Ship ship, BlockPos searchPos, Direction direction, Ship targetShip) {
        final BlockState blockState = world.getBlockState(searchPos);
        if (!AllBlocks.REDSTONE_CONTACT.has(blockState)) {
            return false;
        }
        final Vector3d worldDirection = toJOML(Vec3.atLowerCornerOf(direction.getNormal()));
        final Vector3d targetDirection = toJOML(Vec3.atLowerCornerOf(blockState.getValue(FACING).getNormal()));
        if (ship != null) {
            ship.getShipToWorld().transformDirection(worldDirection);
        }
        if (targetShip != null) {
            targetShip.getShipToWorld().transformDirection(targetDirection);
        }
        final double angle = worldDirection.angleCos(targetDirection);
        return angle < MAX_ALIGNMENT_ANGLE;
    }

    @Inject(method = "hasValidContact", at = @At("RETURN"), cancellable = true)
    private static void injectHasValidContact(LevelAccessor world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
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
        final Vec3 searchPoint = detectPos.getCenter();
        final Vector3d searchPos = toJOML(searchPoint);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship != null) {
            ship.getShipToWorld().transformPosition(searchPos);
        }
        final double bounds = 0.25;
        final AABB searchAABB = new AABB(
            searchPos.x - bounds, searchPos.y - bounds, searchPos.z - bounds,
            searchPos.x + bounds, searchPos.y + bounds, searchPos.z + bounds
        );
        final Vector3d foundPos = new Vector3d(searchPos);
        BlockPos foundBlock = BlockPos.containing(VectorConversionsMCKt.toMinecraft(foundPos));
        boolean found = hasContact(world, ship, foundBlock, direction, null);
        if (!found) {
            for (final Ship targetShip : VSGameUtilsKt.getShipsIntersecting(level, searchAABB)) {
                if (targetShip == ship) {
                    continue;
                }
                targetShip.getWorldToShip().transformPosition(searchPos, foundPos);
                foundBlock = BlockPos.containing(VectorConversionsMCKt.toMinecraft(foundPos));
                if (hasContact(world, ship, foundBlock, direction, targetShip)) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            return;
        }
        final BlockState targetState = world.getBlockState(foundBlock);
        CONTACT_CACHE.put(Pair.of(level, pos), foundBlock);
        if (!targetState.getValue(POWERED)) {
            level.setBlockAndUpdate(foundBlock, targetState.setValue(POWERED, true));
        }
        world.scheduleTick(foundBlock, AllBlocks.REDSTONE_CONTACT.get(), 2, TickPriority.NORMAL);
        cir.setReturnValue(true);
    }
}
