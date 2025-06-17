package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;

import com.mojang.datafixers.util.Pair;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.contact.RedstoneContactBlock;
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
public abstract class MixinRedstoneContactBlock {

    private static Map<Pair<Level, BlockPos>, BlockPos> contactCache = new HashMap<>();

    @Shadow
    @Final
    public static BooleanProperty POWERED;
    @Unique
    private static final double MAX_ALIGNMENT_ANGLE = -0.93972176; //Mth.cos(20*(Mth.DEG_TO_RAD))

    @Inject(method = "onRemove", at = @At("HEAD"))
    private void injectOnRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving, CallbackInfo ci) {
        if (state.getBlock() == RedstoneContactBlock.class.cast(this) && newState.isAir()) {
            Pair<Level, BlockPos> key = Pair.of(worldIn, pos);
            if (state.getValue(POWERED) && contactCache.containsKey(key)) {
                worldIn.scheduleTick(contactCache.get(key), AllBlocks.REDSTONE_CONTACT.get(), 2, TickPriority.NORMAL);
                contactCache.remove(key);
            }
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE_ASSIGN", shift = At.Shift.BY, by = 2, target = "Lcom/simibubi/create/content/redstone/contact/RedstoneContactBlock;hasValidContact(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void injectTick(BlockState state, ServerLevel worldIn, BlockPos pos, RandomSource random, CallbackInfo ci, boolean hasValidContact) {
        if (VSGameUtilsKt.isBlockInShipyard(worldIn, pos)) {
            Pair<Level, BlockPos> key = Pair.of(worldIn, pos);
            if (!hasValidContact && state.getValue(POWERED) && contactCache.containsKey(key)) {
                worldIn.scheduleTick(contactCache.get(key), AllBlocks.REDSTONE_CONTACT.get(), 2, TickPriority.NORMAL);
                contactCache.remove(key);
            }
            worldIn.scheduleTick(pos, AllBlocks.REDSTONE_CONTACT.get(), 2, TickPriority.NORMAL);
        }
    }

    @Unique
    private static boolean hasContact(Level world, Ship ship, Vector3d searchPos, Direction direction, Ship shipItr) {
        BlockState blockState = world.getBlockState(BlockPos.containing(VectorConversionsMCKt.toMinecraft(searchPos)));
        if (AllBlocks.REDSTONE_CONTACT.has(blockState)) {
            Vector3d worldDirection = toJOML(Vec3.atLowerCornerOf(direction.getNormal()));
            Vector3d targetDirection = toJOML(Vec3.atLowerCornerOf(blockState.getValue(RedstoneContactBlock.FACING).getNormal()));
            if (ship != null) {
                ship.getShipToWorld().transformDirection(worldDirection, worldDirection);
            }
            if (shipItr != null) {
                shipItr.getShipToWorld().transformDirection(targetDirection, targetDirection);
            }
            double dotAngle = worldDirection.dot(targetDirection);
            return dotAngle < MAX_ALIGNMENT_ANGLE;
        }
        return false;
    }

    @Inject(method = "hasValidContact", at = @At("RETURN"), cancellable = true, remap = false)
    private static void injectHasValidContact(LevelAccessor world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        boolean result = false;
        Level worldLevel = (Level) world;
        BlockState blockState = world.getBlockState(pos.relative(direction));
        if (AllBlocks.REDSTONE_CONTACT.has(blockState)) {
            cir.setReturnValue(blockState.getValue(RedstoneContactBlock.FACING) == direction.getOpposite());
        } else {

            AABB searchAABB = new AABB(pos.relative(direction));
            Vector3d searchPos = toJOML(Vec3.atCenterOf(pos.relative(direction)));
            Ship ship = VSGameUtilsKt.getShipManagingPos(worldLevel, pos);
            if (VSGameUtilsKt.isBlockInShipyard(worldLevel, pos) && ship != null) {
                Vector3d tempVec = toJOML(Vec3.atCenterOf(pos.relative(direction)));
                searchPos = ship.getShipToWorld().transformPosition(tempVec, new Vector3d());
                ship.getShipToWorld().transformPosition(tempVec, tempVec);
                double bounds = 0.25;
                searchAABB = new AABB(tempVec.x - bounds, tempVec.y - bounds, tempVec.z - bounds,
                        tempVec.x + bounds, tempVec.y + bounds, tempVec.z + bounds);

                result = hasContact(worldLevel, ship, searchPos, direction, null);
            }
            Iterator<Ship> ships = VSGameUtilsKt.getShipsIntersecting(worldLevel, searchAABB).iterator();
            if (ships.hasNext() && !result) {
                do {
                    Ship shipItr = ships.next();
                    if (shipItr == ship) continue;
                    Vector3d newSearchPos = shipItr.getWorldToShip().transformPosition(searchPos, new Vector3d());
                    result = hasContact(worldLevel, ship, newSearchPos, direction, shipItr);
                    if (result) searchPos = newSearchPos;
                } while (ships.hasNext() && !result);
            }
            if (result) {
                contactCache.put(Pair.of(worldLevel, pos), BlockPos.containing(VectorConversionsMCKt.toMinecraft(searchPos)));
                world.scheduleTick(BlockPos.containing(VectorConversionsMCKt.toMinecraft(searchPos)), AllBlocks.REDSTONE_CONTACT.get(), 2, TickPriority.NORMAL);
            }
            cir.setReturnValue(result);
        }
    }
}
