package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.trains.entity.Carriage.DimensionalCarriageEntity;
import com.simibubi.create.content.trains.entity.CarriageSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(CarriageSounds.class)
public class MixinCarriageSounds {
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/Carriage$DimensionalCarriageEntity;leadingAnchor()Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 leadingToWorldSpace(DimensionalCarriageEntity carriage, Operation<Vec3> original) {
        Vec3 anchor = original.call(carriage);
        if (anchor == null) return null;
        return VSGameUtilsKt.toWorldCoordinates(Minecraft.getInstance().level, anchor);
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/Carriage$DimensionalCarriageEntity;trailingAnchor()Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 trailingToWorldSpace(DimensionalCarriageEntity carriage, Operation<Vec3> original) {
        Vec3 anchor = original.call(carriage);
        if (anchor == null) return null;
        return VSGameUtilsKt.toWorldCoordinates(Minecraft.getInstance().level, anchor);
    }
}
