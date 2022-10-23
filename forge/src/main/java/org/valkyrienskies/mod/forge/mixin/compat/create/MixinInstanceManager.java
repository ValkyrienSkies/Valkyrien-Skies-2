package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.jozufozu.flywheel.backend.instancing.IInstance;
import com.jozufozu.flywheel.backend.instancing.InstanceManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.compat.create.CreateCompat;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(InstanceManager.class)
public class MixinInstanceManager {

    @Inject(
        method = "remove",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    void removeFromShip(final Object obj, final CallbackInfo info) {
        if (obj instanceof BlockEntity) {
            final BlockEntity be = (BlockEntity) obj;
            final Ship ship = VSGameUtilsKt.getShipManagingPos(be.getLevel(), be.getBlockPos());
            if (ship != null) {
                info.cancel();
                CreateCompat.INSTANCE.removeBlockEntityFromShip(be.getLevel(), ship, be);
            }
        }
    }

    @Inject(
        method = "createInternal",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    void addToShip(final Object obj, final CallbackInfoReturnable<IInstance> info) {
        if (obj instanceof BlockEntity) {
            final BlockEntity be = (BlockEntity) obj;
            final Ship ship = VSGameUtilsKt.getShipManagingPos(be.getLevel(), be.getBlockPos());
            if (ship != null) {
                info.cancel();
                CreateCompat.INSTANCE.addBlockEntityToShip(be.getLevel(), ship, be);
                info.setReturnValue(CreateCompat.INSTANCE.getIInstance(ship, be));
            }
        }
    }

}
