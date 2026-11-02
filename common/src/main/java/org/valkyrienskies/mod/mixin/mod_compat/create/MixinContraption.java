package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.create.ContraptionSegmentHelper;

@Mixin(Contraption.class)
public class MixinContraption {
    @Redirect(method = "onEntityCreated", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean wrapOp(Level level, Entity entity) {
        // BlockPos anchor = blockFace.getConnectedPos();
        // movedContraption.setPos(anchor.getX() + .5f, anchor.getY(), anchor.getZ() + .5f);
        //
        // Derive anchor from the code above
        final BlockPos anchor = BlockPos.containing((int) Math.floor(entity.getX()), (int) Math.floor(entity.getY()), (int) Math.floor(entity.getZ()));
        boolean added = level.addFreshEntity(entity);
        if (added) {
            entity.moveTo(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5);
        }
        return added;
    }

    @Inject(method = "onEntityCreated", at = @At("HEAD"), remap = false)
    private void preOnEntityCreated(AbstractContraptionEntity entity, CallbackInfo ci) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            ContraptionSegmentHelper.ensureContraptionSegment(serverLevel, entity);
        }
    }
}
