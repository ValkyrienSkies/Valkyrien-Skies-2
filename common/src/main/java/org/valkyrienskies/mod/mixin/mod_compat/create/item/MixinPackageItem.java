package org.valkyrienskies.mod.mixin.mod_compat.create.item;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * This class stops boxes spinning if used on ship to spawn the box entity.
 */

@Mixin(PackageItem.class)
public abstract class MixinPackageItem extends Item {
    public MixinPackageItem(Properties properties) {
        super(properties);
    }

    @WrapOperation(
        method = "useOn",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/context/UseOnContext;getClickLocation()Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 wrapOnUse(UseOnContext context, Operation<Vec3> original){
        final Vec3 pos = original.call(context);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(context.getLevel(), pos);
        if(ship != null) {
            final Vector3d worldPos = ship.getShipToWorld().transformPosition(pos.x, pos.y, pos.z , new Vector3d());
            return VectorConversionsMCKt.toMinecraft(worldPos);
        }
        return pos;
    }
}
