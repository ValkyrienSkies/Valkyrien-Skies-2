package org.valkyrienskies.mod.mixin.mod_compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

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

    @Inject(method = "Lcom/simibubi/create/content/schematics/cannon/LaunchedItem;update(Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"))
    private void injectUpdate(Level world, CallbackInfoReturnable<Boolean> cir) {
        if (this.valkyrienskies$startPos == null) {
            return;
        }

        LaunchedItem launchedItem = LaunchedItem.class.cast(this);
        Ship targetShip = VSGameUtilsKt.getShipObjectManagingPos(world, launchedItem.target);
        Ship thisShip = VSGameUtilsKt.getShipObjectManagingPos(world, this.valkyrienskies$startPos);

        Vector3d newPos = VectorConversionsMCKt.toJOML(launchedItem.target.getCenter());

        // If we're on the same ship as where we're placing, don't change behaviour
        if (targetShip == thisShip) {
            return;
        }

        // Transform target thisShip -> world
        if (targetShip != null) {
            newPos = targetShip.getTransform().getShipToWorld().transformPosition(newPos);
        }

        // If we're on a ship, transform target from world -> our ship
        if (thisShip != null) {
            newPos = thisShip.getTransform().getWorldToShip().transformPosition(newPos);
        }

        // Estimate a world (or our ship) blockpos, just for basic distance checks
        BlockPos estimatePos = BlockPos.containing(VectorConversionsMCKt.toMinecraft(newPos));

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
