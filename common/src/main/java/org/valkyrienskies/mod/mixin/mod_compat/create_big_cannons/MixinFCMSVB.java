package org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.mod.common.util.ShipAwareHit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity.FixedCannonMountScrollValueBehaviour;

@Mixin(FixedCannonMountScrollValueBehaviour.class)
public abstract class MixinFCMSVB extends BlockEntityBehaviour {
    public MixinFCMSVB(SmartBlockEntity be) {
        super(be);
    }

    @WrapOperation(
        method = "testHit",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    public Vec3 redirectSubtract(Vec3 instance, Vec3 vec, Operation<Vec3> original) {
        return ShipAwareHit.subtract(this.getWorld(), instance, vec);
    }
}
