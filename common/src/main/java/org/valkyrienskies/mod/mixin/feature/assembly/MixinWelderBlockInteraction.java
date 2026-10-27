package org.valkyrienskies.mod.mixin.feature.assembly;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.item.ShipWelderItem;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinWelderBlockInteraction {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void vs$selectWelderFace(Level level, Player player, InteractionHand hand, BlockHitResult hit,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        if (player.getItemInHand(hand).getItem() instanceof ShipWelderItem) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
