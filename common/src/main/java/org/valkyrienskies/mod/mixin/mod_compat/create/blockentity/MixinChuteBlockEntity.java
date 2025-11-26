package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ChuteBlockEntity.class)
public abstract class MixinChuteBlockEntity extends SmartBlockEntity {

    public MixinChuteBlockEntity(BlockEntityType<?> type,
        BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @WrapOperation(
        method = "findEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;getCenter()Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 wrapStartingPos(AABB aabb, Operation<Vec3> original) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, getBlockPos());
        if(ship != null) {
            final Vector3d shipPos = VectorConversionsMCKt.toJOML(original.call(aabb));
            ship.getWorldToShip().transformPosition(shipPos);
            return VectorConversionsMCKt.toMinecraft(shipPos);
        } else return original.call(aabb);
    }
}
