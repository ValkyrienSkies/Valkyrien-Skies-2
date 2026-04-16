package org.valkyrienskies.mod.mixin.feature.physics_entities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.api.EntityPhysicsListener;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

@Mixin(ServerLevel.EntityCallbacks.class)
public class MixinServerLevel {

    @Inject(method = "onTickingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void postAddEntity(Entity entity, CallbackInfo ci) {
        if (entity instanceof EntityPhysicsListener listener) {
            String dimid = VSGameUtilsKt.getDimensionId((ServerLevel) (Object) this);
            ValkyrienSkiesMod.INSTANCE.addEntityPhysTicker(dimid, entity);
        }
    }

    @Inject(method = "onTickingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void postRemoveEntity(Entity entity, CallbackInfo ci) {
        if (entity instanceof EntityPhysicsListener listener) {
            String dimid = VSGameUtilsKt.getDimensionId((ServerLevel) (Object) this);
            ValkyrienSkiesMod.INSTANCE.removeEntityPhysTicker(entity, dimid);
        }
    }
}
