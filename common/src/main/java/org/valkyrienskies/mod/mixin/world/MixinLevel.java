package org.valkyrienskies.mod.mixin.world;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.game.ships.ShipObjectServer;
import org.valkyrienskies.mod.api.ShipBlockEntity;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Level.class)
public class MixinLevel {
    @Unique
    private static final Logger LOGGER = LogManager.getLogger();

    @Shadow
    @Final
    public boolean isClientSide;

    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    public void onSetBlockEntity(final BlockPos blockPos, final BlockEntity blockEntity, final CallbackInfo ci) {
        if (!this.isClientSide) {
            final ShipObjectServer obj = VSGameUtilsKt.getShipObjectManagingPos((ServerLevel) (Object) this, blockPos);
            if (obj != null && blockEntity instanceof ShipBlockEntity) {
                ((ShipBlockEntity) blockEntity).setShip(obj);
            }
        }
    }

    @Inject(
        method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("HEAD"), cancellable = true)
    public void checkAABB1(@Nullable final Entity entity,
        final AABB area,
        @Nullable final Predicate<? super Entity> predicate,
        final CallbackInfoReturnable<List<Entity>> cir) {

        if (isCollisionBoxToBig(area)) {
            LOGGER.error("Collision box is too big! " + area + " returning empty list! this might break things");
            cir.setReturnValue(Collections.EMPTY_LIST);
            cir.cancel();
        }
    }

    @Inject(
        method = "getLoadedEntitiesOfClass",
        at = @At("HEAD"), cancellable = true)
    public <T extends Entity> void checkAABB2(final Class<? extends T> clazz, final AABB area,
        @Nullable final Predicate<? super T> predicate,
        final CallbackInfoReturnable<List<T>> cir) {

        if (isCollisionBoxToBig(area)) {
            LOGGER.error("Collision box is too big! " + area + " returning empty list! this might break things");
            cir.setReturnValue(Collections.EMPTY_LIST);
            cir.cancel();
        }
    }

    @Inject(
        method = "getEntitiesOfClass",
        at = @At("HEAD"), cancellable = true)
    public <T extends Entity> void checkAABB3(
        final Class<? extends T> clazz,
        final AABB area,
        @Nullable final Predicate<? super T> filter,
        final CallbackInfoReturnable<List<T>> cir) {

        if (isCollisionBoxToBig(area)) {
            LOGGER.error("Collision box is too big! " + area + " returning empty list! this might break things");
            cir.setReturnValue(Collections.EMPTY_LIST);
            cir.cancel();
        }
    }

    @Inject(
        method = "getEntities(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("HEAD"), cancellable = true)
    public <T extends Entity> void checkAABB4(@Nullable final EntityType<T> entityType,
        final AABB area,
        final Predicate<? super T> predicate,
        final CallbackInfoReturnable<List<T>> cir) {

        if (isCollisionBoxToBig(area)) {
            LOGGER.error("Collision box is too big! " + area + " returning empty list! this might break things");
            cir.setReturnValue(Collections.EMPTY_LIST);
            cir.cancel();
        }
    }

    @Unique
    public boolean isCollisionBoxToBig(final AABB aabb) {
        return aabb.getXsize() > 1000 || aabb.getYsize() > 1000 || aabb.getZsize() > 1000;
    }
}
