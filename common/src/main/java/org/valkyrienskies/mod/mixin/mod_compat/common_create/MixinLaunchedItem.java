package org.valkyrienskies.mod.mixin.mod_compat.common_create;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LaunchedItem.class)
public class MixinLaunchedItem {
    @Shadow
    public BlockPos target;
    @Unique
    public BlockPos valkyrienskies$startPos;

    @Inject(
        method = "<init>(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",  // the jvm bytecode signature for the constructor
        at = @At("RETURN")
    )
    private void constructorOne(
        BlockPos start, BlockPos target, ItemStack stack, CallbackInfo ci
    ) {
        this.valkyrienskies$startPos = start;
    }

    @Inject(method = "update(Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"))
    private void injectUpdate(Level world, CallbackInfoReturnable<Boolean> cir) {
        if (this.valkyrienskies$startPos == null) {
            return;
        }

        final LaunchedItem launchedItem = LaunchedItem.class.cast(this);
        final Ship thisShip = VSGameUtilsKt.getShipObjectManagingPos(world, this.valkyrienskies$startPos);

        BlockPos estimatePos = BlockPos.containing(
            CompatUtil.INSTANCE.toSameSpaceAs(
                world,
                launchedItem.target.getCenter(),
                thisShip
            )
        );

        // If our distance to the transformed postion is less than the default distance
        // (which it will be if the default went to the shipyard or back)
        // Set the distance to a REASONABLE value so the block gets placed in time
        if (ticksForDistance(this.valkyrienskies$startPos, estimatePos) < launchedItem.totalTicks) {
            launchedItem.totalTicks = ticksForDistance(this.valkyrienskies$startPos, estimatePos);
            launchedItem.ticksRemaining = launchedItem.totalTicks;
        }
    }

    @Shadow
    private static int ticksForDistance(BlockPos start, BlockPos target) {
        return 0;
    }
}
