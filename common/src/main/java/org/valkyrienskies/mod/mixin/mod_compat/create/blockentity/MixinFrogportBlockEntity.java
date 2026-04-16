package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(FrogportBlockEntity.class)
public abstract class MixinFrogportBlockEntity extends PackagePortBlockEntity {
    @Inject(
        method = "lazyTick",
        at = @At(
            // We cancel specifically before placing packages onto the conveyor,
            // retrieval has to be handled in another mixin anyway.
            value = "INVOKE", target = "Lcom/simibubi/create/content/logistics/packagePort/frogport/FrogportBlockEntity;tryPullingFromOwnAndAdjacentInventories()V"
        ),
        cancellable = true
    )
    private void cancelIfTooFar(CallbackInfo ci) {
        BlockPos targetPos = getBlockPos().offset(target != null ? target.relativePos : BlockPos.ZERO);
        if (
            VSGameUtilsKt.getShipManagingPos(level, worldPosition) == VSGameUtilsKt.getShipManagingPos(level, targetPos)
        ) return;
        double dist = CompatUtil.INSTANCE.toSameSpaceAs(
            level,
            getBlockPos().getCenter(),
            targetPos.getCenter()
        ).distanceTo(targetPos.getCenter());
        if (dist > (double)((Integer) AllConfigs.server().logistics.packagePortRange.get() + 2)) { // copied from Create
            ci.cancel();
        }
    }

    // Why isn't this client-side?
    @WrapOperation(
        method = "getYaw",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packagePort/PackagePortTarget;getExactTargetLocation(Lcom/simibubi/create/content/logistics/packagePort/PackagePortBlockEntity;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 adjustPosition(PackagePortTarget instance, PackagePortBlockEntity packagePortBlockEntity,
        LevelAccessor levelAccessor, BlockPos blockPos, Operation<Vec3> original) {
        return CompatUtil.INSTANCE.toSameSpaceAs(
            packagePortBlockEntity.getLevel(),
            original.call(instance, packagePortBlockEntity, levelAccessor, blockPos),
            blockPos
        );
    }

    // Dummy
    public MixinFrogportBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }
}
