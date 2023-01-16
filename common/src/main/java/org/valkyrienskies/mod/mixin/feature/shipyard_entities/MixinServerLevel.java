package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;

@Mixin(ServerLevel.class)
public class MixinServerLevel {

    @Shadow
    @Final
    private PersistentEntitySectionManager<Entity> entityManager;

    @Inject(method = "<init>", at = @At("RETURN"))
    void configureEntitySections(final CallbackInfo ci) {
        ((OfLevel) entityManager).setLevel(ServerLevel.class.cast(this));
    }

    @Inject(
        method = "addEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addNewEntity(Lnet/minecraft/world/level/entity/EntityAccess;)Z"
        )
    )
    void preAddEntity(final Entity entity, final CallbackInfoReturnable<Boolean> cir) {
        final LoadedShip ship = VSGameUtilsKt.getShipObjectManagingPos(entity.level, VectorConversionsMCKt.toJOML(entity.position()));
        if (ship != null) {
            VSEntityManager.INSTANCE.getHandler(entity).freshEntityInShipyard(entity, ship);
        }
    }

}
