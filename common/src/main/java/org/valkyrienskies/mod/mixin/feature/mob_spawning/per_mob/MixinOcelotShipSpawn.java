package org.valkyrienskies.mod.mixin.feature.mob_spawning.per_mob;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Ocelot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(value = Ocelot.class, priority = 1500)
public abstract class MixinOcelotShipSpawn {

    @WrapOperation(
        method = "checkSpawnObstruction",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;getY()I"
        ),
        require = 0
    )
    private int vs$shipOcelotY(final BlockPos pos, final Operation<Integer> original) {
        return VSGameUtilsKt.shipProjectedWorldY(
            ((Ocelot) (Object) this).level(), pos, original.call(pos)
        );
    }
}
