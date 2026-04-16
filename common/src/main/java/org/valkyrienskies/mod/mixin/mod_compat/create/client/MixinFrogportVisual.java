package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.CompatUtil;

@Mixin(FrogportVisual.class)
public class MixinFrogportVisual {
    // SchematicannonVisual calls methods of SchematicannonRenderer for calculating angles and positions. With frogports
    // BE renderer and FW visual have these calculations duplicate and separate from each other.
    @WrapOperation(
        method = "animate",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packagePort/PackagePortTarget;getExactTargetLocation(Lcom/simibubi/create/content/logistics/packagePort/PackagePortBlockEntity;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    Vec3 adjustPosition(PackagePortTarget instance, PackagePortBlockEntity packagePortBlockEntity,
        LevelAccessor levelAccessor, BlockPos blockPos, Operation<Vec3> original) {
        return CompatUtil.INSTANCE.toSameSpaceAs(
            packagePortBlockEntity.getLevel(),
            original.call(instance, packagePortBlockEntity, levelAccessor, blockPos),
            blockPos
        );
    }
}
