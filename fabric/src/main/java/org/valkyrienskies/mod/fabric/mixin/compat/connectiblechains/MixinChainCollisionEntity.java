package org.valkyrienskies.mod.fabric.mixin.compat.connectiblechains;

import com.github.legoatoom.connectiblechains.entity.ChainCollisionEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ChainCollisionEntity.class)
public abstract class MixinChainCollisionEntity extends Entity {

    // Collisions with shipyard entities (bar contraptions, maybe) are very weird and can even lock your camera.
    // These entities aren't any special, they are just invisible boxes of sub-block size, slightly larger on Y axis.
    // This mixin will not be necessary in the case collisions become stable.
    @Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
    private void disableOnShips(CallbackInfoReturnable<Boolean> cir) {
        if (VSGameUtilsKt.isBlockInShipyard(level(), position())) {
            cir.setReturnValue(false);
        }
    }

    // Dummy
    public MixinChainCollisionEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }
}
