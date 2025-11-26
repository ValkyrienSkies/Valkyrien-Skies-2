package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.chainConveyor.ChainPackageInteractionHandler;
import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ChainPackageInteractionHandler.class)
public abstract class MixinChainPackageInteractionHandler {

    @WrapOperation(
        method = "lambda$onUse$0",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;")
    )
    private static Optional<Vec3> wrapAABB(AABB instance, Vec3 from, Vec3 to, Operation<Optional<Vec3>> original){
        ClientShip ship = VSClientGameUtils.getClientShip(instance.getCenter().x, instance.getCenter().y, instance.getCenter().z);
        if(ship != null){
            AABBd aabBd = VectorConversionsMCKt.toJOML(instance);
            aabBd = aabBd.transform(ship.getTransform().getShipToWorld());
            return original.call(VectorConversionsMCKt.toMinecraft(aabBd), from, to);
        }
        return original.call(instance, from, to);
    }
}
