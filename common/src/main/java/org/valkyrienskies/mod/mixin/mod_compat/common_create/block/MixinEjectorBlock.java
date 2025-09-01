package org.valkyrienskies.mod.mixin.mod_compat.common_create.block;

import com.simibubi.create.content.logistics.depot.EjectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.CompatUtil;

@Mixin(EjectorBlock.class)
public abstract class MixinEjectorBlock {
    @Redirect(method = "updateEntityAfterFallOn", at = @At(
            value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"
    ))
    private BlockPos redirectBlockPosition(Entity instance) {
        return instance.getOnPos();
    }

    @Redirect(method = "updateEntityAfterFallOn", at = @At(
            value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"
    ))
    private Vec3 redirectEntityPosition(Entity instance) {
        return CompatUtil.INSTANCE.toSameSpaceAs(instance.level(), instance.position(), instance.getOnPos());
    }

    @Redirect(method = "updateEntityAfterFallOn", at = @At(
            value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V"
    ))
    private void redirectSetPos(Entity instance, double x, double y, double z) {
        instance.setPos(
            CompatUtil.INSTANCE.toSameSpaceAs(instance.level(), x, y, z, instance.getOnPos())
        );
    }
}
