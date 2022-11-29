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

}
