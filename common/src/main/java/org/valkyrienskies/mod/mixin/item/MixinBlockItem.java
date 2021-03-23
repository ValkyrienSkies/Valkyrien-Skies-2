package org.valkyrienskies.mod.mixin.item;

import java.util.Objects;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.hit.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.mixinducks.client.MinecraftClientDuck;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

    @Shadow
    @Nullable
    protected abstract BlockState getPlacementState(ItemPlacementContext context);

    @Redirect(
        method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/BlockItem;getPlacementState(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/block/BlockState;"
        )
    )
    public BlockState makeSlabsWork(final BlockItem receiver, final ItemPlacementContext ctx) {
        return this.getPlacementState(new ItemPlacementContext(
            Objects.requireNonNull(ctx.getPlayer(), "Blame Ruby"),
            ctx.getHand(),
            ctx.getStack(),
            (BlockHitResult) ((MinecraftClientDuck) MinecraftClient.getInstance()).vs$getOriginalCrosshairTarget()
        ));
    }

}
