package org.valkyrienskies.mod.fabric.mixin.compat.connectiblechains;

import com.github.legoatoom.connectiblechains.entity.Chainable;
import com.github.legoatoom.connectiblechains.entity.Chainable.ChainData;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Chainable.class)
public interface MixinChainable {
    // Chainable is an interface and tickChain accepts a generic. We are lucky it is only ever used once, for HangingEntity
    // so we can use it as a Local. Forge port handles this in a far less overengineered way.
    @WrapOperation(method = "tickChain", at = @At(value = "INVOKE", target = "Lcom/github/legoatoom/connectiblechains/entity/Chainable;getMaxChainLength()D"))
    private static double adjustMaxChainLength(Operation<Double> original, @Local(argsOnly = true) HangingEntity entity) {
        Ship ship = VSGameUtilsKt.getShipManaging(entity);
        if (ship != null) {
            Vector3dc scale = ship.getTransform().getScaling();
            return original.call() * scale.get(scale.maxComponent()); // here we get max length so multiplying
        } else {
            return original.call();
        }
    }

    @WrapWithCondition(
        method = "attachChain(Lnet/minecraft/world/entity/decoration/HangingEntity;Lcom/github/legoatoom/connectiblechains/entity/Chainable$ChainData;Lnet/minecraft/world/entity/Entity;Z)V",
        at = @At(value = "INVOKE", target = "Lcom/github/legoatoom/connectiblechains/entity/ChainCollisionEntity;createCollision(Lnet/minecraft/world/entity/Entity;Lcom/github/legoatoom/connectiblechains/entity/Chainable$ChainData;)V")
    )
    private static boolean skipCreatingCollision(Entity entity, ChainData chainData) {
        // Created collision entities are created between two nodes of the chain. Not applicable between world and ship, or different ships.
        return VSGameUtilsKt.getShipManaging(entity) == VSGameUtilsKt.getShipManaging(((Chainable)entity).getChainHolder(chainData));
    }
}
