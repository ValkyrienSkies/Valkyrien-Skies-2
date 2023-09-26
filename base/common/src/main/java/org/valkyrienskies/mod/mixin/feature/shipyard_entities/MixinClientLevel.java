package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;

@Mixin(ClientLevel.class)
public class MixinClientLevel {

    @Shadow
    @Final
    private TransientEntitySectionManager<Entity> entityStorage;

    @Inject(method = "<init>", at = @At("RETURN"))
    void configureEntitySections(final CallbackInfo ci) {
        ((OfLevel) entityStorage).setLevel(ClientLevel.class.cast(this));
    }

}
