package org.valkyrienskies.mod.mixin.mod_compat.create;

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
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.SplitHandler;
import org.valkyrienskies.mod.common.util.SplittingDisablerAttachment;

@Mixin(Contraption.class)
public class MixinContraption {
    @Redirect(method = "onEntityCreated", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean wrapOp(Level level, Entity entity) {
        // BlockPos anchor = blockFace.getConnectedPos();
        // movedContraption.setPos(anchor.getX() + .5f, anchor.getY(), anchor.getZ() + .5f);
        //
        // Derive anchor from the code above
        final BlockPos anchor = new BlockPos((int) Math.floor(entity.getX()), (int) Math.floor(entity.getY()), (int) Math.floor(entity.getZ()));
        boolean added = level.addFreshEntity(entity);
        if (added) {
            entity.moveTo(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5);
        }
        return added;
    }

    @Inject(method = "removeBlocksFromWorld", at = @At("HEAD"))
    private void preRemoveBlocksFromWorld(Level world, BlockPos pos, CallbackInfo ci) {
        if (world instanceof ServerLevel sWorld) {
            LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(sWorld, pos);
            if (ship != null) {
                SplittingDisablerAttachment attachment = ship.getAttachment(SplittingDisablerAttachment.class);
                if (attachment != null) {
                    attachment.disableSplitting();
                }
            }
        }
    }

    @Inject(method = "removeBlocksFromWorld", at = @At("RETURN"))
    private void postRemoveBlocksFromWorld(Level world, BlockPos pos, CallbackInfo ci) {
        if (world instanceof ServerLevel sWorld) {
            LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(sWorld, pos);
            if (ship != null) {
                SplittingDisablerAttachment attachment = ship.getAttachment(SplittingDisablerAttachment.class);
                if (attachment != null) {
                    attachment.enableSplitting();
                }
            }
        }
    }
}
