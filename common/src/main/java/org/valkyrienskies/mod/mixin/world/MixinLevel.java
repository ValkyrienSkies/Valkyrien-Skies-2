package org.valkyrienskies.mod.mixin.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectServer;
import org.valkyrienskies.mod.api.ShipBlockEntity;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Level.class)
public class MixinLevel {

    @Shadow
    @Final
    public boolean isClientSide;

    @ModifyVariable(
        method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("HEAD"),
        argsOnly = true
    )
    public AABB moveAABB1(final AABB aabb) {
        return VSGameUtilsKt.transformAabbToWorld(Level.class.cast(this), aabb);
    }

    @ModifyVariable(
        method = "getEntities(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("HEAD"),
        argsOnly = true
    )
    public AABB moveAABB2(final AABB aabb) {
        return VSGameUtilsKt.transformAabbToWorld(Level.class.cast(this), aabb);
    }

    @ModifyVariable(
        method = "getEntitiesOfClass",
        at = @At("HEAD"),
        argsOnly = true
    )
    public AABB moveAABB3(final AABB aabb) {
        return VSGameUtilsKt.transformAabbToWorld(Level.class.cast(this), aabb);
    }

    @ModifyVariable(
        method = "getLoadedEntitiesOfClass",
        at = @At("HEAD"),
        argsOnly = true
    )
    public AABB moveAABB4(final AABB aabb) {
        return VSGameUtilsKt.transformAabbToWorld(Level.class.cast(this), aabb);
    }

    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    public void onSetBlockEntity(final BlockPos blockPos, final BlockEntity blockEntity, final CallbackInfo ci) {
        if (!this.isClientSide) {
            final ShipObjectServer obj = VSGameUtilsKt.getShipObjectManagingPos(ServerLevel.class.cast(this), blockPos);
            if (obj != null && blockEntity instanceof ShipBlockEntity) {
                ((ShipBlockEntity) blockEntity).setShip(obj);
            }
        }
    }

}
