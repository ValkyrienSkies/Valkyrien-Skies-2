package org.valkyrienskies.mod.forge.mixin.compat.connectiblechains;

import com.lilypuree.connectiblechains.chain.ChainLink;
import com.lilypuree.connectiblechains.entity.ChainKnotEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(value = ChainKnotEntity.class, remap = false)
public abstract class MixinChainKnotEntity {
    @WrapOperation(method = "updateLinks", at = @At(value = "INVOKE", target = "Lcom/lilypuree/connectiblechains/chain/ChainLink;getSquaredDistance()D"))
    double adjustMaxChainLength(ChainLink instance, Operation<Double> original) {
        Ship ship = VSGameUtilsKt.getShipManaging(instance.primary);
        if (ship != null) {
            Vector3dc scale = ship.getTransform().getScaling();
            return original.call(instance) / scale.get(scale.maxComponent()); // here we mixin a distance check so dividing
        } else {
            return original.call(instance);
        }
    }
}
