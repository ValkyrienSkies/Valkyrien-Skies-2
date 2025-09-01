package org.valkyrienskies.mod.mixin.mod_compat.common_create.block;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlock;
import com.simibubi.create.content.logistics.chute.AbstractChuteBlock;
import com.simibubi.create.content.processing.basin.BasinBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.mixinducks.world.entity.EntityDuck;

@Mixin(value = {
    MillstoneBlock.class,
    BasinBlock.class,
    AbstractChuteBlock.class
})
public abstract class MixinBlocks {

    @Redirect(
        method = "updateEntityAfterFallOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"
        ),
        require = 0 // The mixin is a blanket solution, a specific block might not be using this particular function.
    )
    private BlockPos redirectBlockPosition(final Entity entity) {
        return BlockPos.containing(
            CompatUtil.INSTANCE.toSameSpaceAs(entity.level(), entity.position().add(0, 0.5, 0), ((EntityDuck)entity).vs_getSteppedOn())
        );
    }

    @WrapOperation(
        method = "updateEntityAfterFallOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"
        ),
        require = 0
    )
    private Vec3 redirectPosition(final Entity entity, Operation<Vec3> original) {
        return CompatUtil.INSTANCE.toSameSpaceAs(entity.level(), original.call(entity), ((EntityDuck)entity).vs_getSteppedOn());
    }
}
