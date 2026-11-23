package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.compat.create.ChainConveyorFrames;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.ChainConveyorCrossFrameDuck;

// Connections between a ship and the world (or two ships): see ChainConveyorFrames. Their stats follow the ships.
@Mixin(ChainConveyorBlockEntity.class)
public abstract class MixinChainConveyorCrossFrame implements ChainConveyorCrossFrameDuck {

    @Shadow
    public Set<BlockPos> connections;

    @Shadow
    public Map<BlockPos, ChainConveyorBlockEntity.ConnectionStats> connectionStats;

    @Shadow
    public abstract float wrapAngle(float angle);

    // The chain's length changes as the ship moves, so what it costs and refunds is fixed when it's connected.
    @Unique
    private final Map<BlockPos, Integer> vs$chainsPaid = new HashMap<>();

    @Override
    public Integer vs$chainsPaid(final BlockPos connection) {
        return vs$chainsPaid.get(connection);
    }

    @Inject(method = "addConnectionTo", at = @At("RETURN"))
    private void vs$recordChains(final BlockPos target, final CallbackInfoReturnable<Boolean> cir) {
        final ChainConveyorBlockEntity self = (ChainConveyorBlockEntity) (Object) this;
        final BlockPos connection = target.subtract(self.getBlockPos());
        if (cir.getReturnValueZ() && ChainConveyorFrames.isCrossFrame(self.getLevel(), self.getBlockPos(), connection)) {
            vs$chainsPaid.put(connection, ChainConveyorFrames.chainCost(self.getLevel(), self.getBlockPos(), connection));
        }
    }

    @Inject(method = "removeConnectionTo", at = @At("RETURN"))
    private void vs$forgetChains(final BlockPos target, final CallbackInfoReturnable<Boolean> cir) {
        vs$chainsPaid.remove(target.subtract(((ChainConveyorBlockEntity) (Object) this).getBlockPos()));
    }

    @Inject(method = "write", at = @At("TAIL"), remap = false)
    private void vs$writeChains(final CompoundTag tag, final boolean clientPacket, final CallbackInfo ci) {
        if (vs$chainsPaid.isEmpty()) return;
        final ListTag list = new ListTag();
        vs$chainsPaid.forEach((connection, count) -> {
            final CompoundTag entry = NbtUtils.writeBlockPos(connection);
            entry.putInt("VSChains", count);
            list.add(entry);
        });
        tag.put("VSChainsPaid", list);
    }

    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void vs$readChains(final CompoundTag tag, final boolean clientPacket, final CallbackInfo ci) {
        vs$chainsPaid.clear();
        for (final Tag t : tag.getList("VSChainsPaid", Tag.TAG_COMPOUND)) {
            final CompoundTag entry = (CompoundTag) t;
            final BlockPos connection = NbtUtils.readBlockPos(entry);
            if (connections.contains(connection)) vs$chainsPaid.put(connection, entry.getInt("VSChains"));
        }
    }

    @Shadow
    private void updateChainShapes() {
    }

    // Create's calculateConnectionStats with the frame-aware offset.
    @Inject(method = "calculateConnectionStats", at = @At("HEAD"), cancellable = true)
    private void vs$crossFrameStats(final BlockPos connection, final CallbackInfo ci) {
        final ChainConveyorBlockEntity self = (ChainConveyorBlockEntity) (Object) this;
        final Level level = self.getLevel();
        final BlockPos pos = self.getBlockPos();
        if (connectionStats == null || !ChainConveyorFrames.isCrossFrame(level, pos, connection)) return;
        final Vec3 offset = ChainConveyorFrames.offset(level, pos, connection);
        final int sign = ((KineticBlockEntity) self).getSpeed() < 0 ? -1 : 1;
        final float branch = 35.0f;
        final float direction = Mth.RAD_TO_DEG * (float) Mth.atan2(offset.x, offset.z);
        final float angle = wrapAngle(direction - branch * sign);
        final float opposite = wrapAngle(angle + 180.0f + 2.0f * branch * sign);
        final Vec3 base = Vec3.atBottomCenterOf(pos);
        final Vec3 start = base.add(VecHelper.rotate(new Vec3(0, 0, 1.25), angle, Direction.Axis.Y)).add(0, 0.375, 0);
        final Vec3 end = base.add(offset).add(VecHelper.rotate(new Vec3(0, 0, 1.25), opposite, Direction.Axis.Y))
            .add(0, 0.375, 0);
        final Vec3 d = end.subtract(start);
        final float length = (float) Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z);
        connectionStats.put(connection, new ChainConveyorBlockEntity.ConnectionStats(angle, length, start, end));
        ci.cancel();
    }

    @Override
    public boolean vs$refreshCrossFrame() {
        final ChainConveyorBlockEntity self = (ChainConveyorBlockEntity) (Object) this;
        if (connectionStats == null) return false;
        boolean moved = false;
        for (final BlockPos connection : connections) {
            if (!ChainConveyorFrames.isCrossFrame(self.getLevel(), self.getBlockPos(), connection)) continue;
            final ChainConveyorBlockEntity.ConnectionStats before = connectionStats.get(connection);
            vs$crossFrameStats(connection, new CallbackInfo("calculateConnectionStats", true));
            final ChainConveyorBlockEntity.ConnectionStats after = connectionStats.get(connection);
            if (before == null || after == null || before.end().distanceToSqr(after.end()) > 1.0E-4) moved = true;
        }
        return moved;
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void vs$followShips(final CallbackInfo ci) {
        if (vs$refreshCrossFrame()) updateChainShapes();
        final ChainConveyorBlockEntity self = (ChainConveyorBlockEntity) (Object) this;
        final Level level = self.getLevel();
        if (level == null || level.isClientSide) return;
        // A chain stretched past what its chains cover snaps.
        final BlockPos pos = self.getBlockPos();
        for (final BlockPos connection : List.copyOf(connections)) {
            if (!ChainConveyorFrames.isCrossFrame(level, pos, connection)) continue;
            if (ChainConveyorFrames.offset(level, pos, connection).length() <= ChainConveyorFrames.maxLength(self, connection)) continue;
            final BlockPos target = pos.offset(connection);
            if (!level.isLoaded(target) || !(level.getBlockEntity(target) instanceof final ChainConveyorBlockEntity other)) {
                continue;
            }
            self.chainDestroyed(connection, true, true);
            other.removeConnectionTo(pos);
            self.removeConnectionTo(target);
        }
    }

    // A package arriving from another frame enters the loop where the chain meets this conveyor in its own frame.
    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;addLoopingPackage(Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorPackage;)Z")
    )
    private boolean vs$arrivalAngle(final ChainConveyorBlockEntity receiver, final ChainConveyorPackage box,
        final Operation<Boolean> original) {
        final ChainConveyorBlockEntity self = (ChainConveyorBlockEntity) (Object) this;
        final BlockPos back = self.getBlockPos().subtract(receiver.getBlockPos());
        if (ChainConveyorFrames.isCrossFrame(self.getLevel(), receiver.getBlockPos(), back)) {
            box.chainPosition = receiver.wrapAngle(
                ChainConveyorFrames.arrivalAngle(self.getLevel(), receiver.getBlockPos(), back, self.reversed));
        }
        return original.call(receiver, box);
    }

    // Package flip length and destroy particles use the actual connection length.
    @WrapOperation(
        method = {"tick", "spawnDestroyParticles"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 vs$actualOffset(final Vec3i connection, final Operation<Vec3> original) {
        final ChainConveyorBlockEntity self = (ChainConveyorBlockEntity) (Object) this;
        if (!(connection instanceof final BlockPos c)) return original.call(connection);
        return ChainConveyorFrames.offset(self.getLevel(), self.getBlockPos(), c);
    }

    @WrapOperation(
        method = "chainDestroyed",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;getChainCost(Lnet/minecraft/core/BlockPos;)I")
    )
    private int vs$actualCost(final BlockPos connection, final Operation<Integer> original) {
        return ChainConveyorFrames.refund((ChainConveyorBlockEntity) (Object) this, connection);
    }
}
