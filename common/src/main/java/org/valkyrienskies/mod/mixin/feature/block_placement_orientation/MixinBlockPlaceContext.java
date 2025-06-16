package org.valkyrienskies.mod.mixin.feature.block_placement_orientation;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.PlayerUtil;

@Mixin(BlockPlaceContext.class)
public abstract class MixinBlockPlaceContext extends UseOnContext {

    public MixinBlockPlaceContext(final Player player, final InteractionHand interactionHand,
        final BlockHitResult blockHitResult) {
        super(player, interactionHand, blockHitResult);
        throw new AssertionError();
    }

    @Shadow
    public abstract BlockPos getClickedPos();

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/Direction;orderedByNearest(Lnet/minecraft/world/entity/Entity;)[Lnet/minecraft/core/Direction;"
        ),
        method = "getNearestLookingDirections"
    )
    private Direction[] transformPlayerBeforeGettingNearest(final Entity entity,
        final Operation<Direction[]> orderedByNearest) {
        if (entity instanceof Player) {
            return PlayerUtil.transformPlayerTemporarily((Player) entity, this.getLevel(), this.getClickedPos(),
                () -> orderedByNearest.call(entity));
        } else {
            return orderedByNearest.call(entity);
        }
    }

}
