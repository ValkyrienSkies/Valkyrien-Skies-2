package org.valkyrienskies.mod.mixin.block;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

    /**
     * Rotates the placement angle based if building on a ship
     *
     * @author ewoudje
     */
    @Nullable
    @Overwrite
    public ActionResult place(final ItemPlacementContext ctx) {
        if (!ctx.canPlace()) {
            return ActionResult.FAIL;
        }
        final ItemPlacementContext ctx2 = this.getPlacementContext(ctx);
        if (ctx2 == null) {
            return ActionResult.FAIL;
        }

        //start
        final float oldYaw = ctx.getPlayer().yaw;
        final float oldPitch = ctx.getPlayer().pitch;

        final ShipObject result = VSGameUtilsKt.getShipObjectManagingPos(ctx2.getWorld(), ctx2.getBlockPos());
        if (result != null) {
            final Vector3d direction = result.getShipData().getShipTransform()
                .getWorldToShipMatrix()
                .transformDirection(VectorConversionsMCKt.toJOML(ctx2.getPlayer().getRotationVector()));
            
            double yaw = Math.atan2(direction.x, -direction.z); //yaw in radians
            double pitch = Math.asin(-direction.y);
            ctx2.getPlayer().yaw = (float) (yaw * (180 / Math.PI)) + 180;
            ctx2.getPlayer().pitch = (float) (pitch * (180 / Math.PI));
        }
        //end

        final BlockState blockState = this.getPlacementState(ctx2);
        if (blockState == null) {
            return ActionResult.FAIL;
        }

        if (!this.place(ctx2, blockState)) {
            return ActionResult.FAIL;
        }

        //start
        ctx.getPlayer().yaw = oldYaw;
        ctx.getPlayer().pitch = oldPitch;
        //end

        final BlockPos blockPos = ctx2.getBlockPos();
        final World world = ctx2.getWorld();
        final PlayerEntity playerEntity = ctx2.getPlayer();
        final ItemStack itemStack = ctx2.getStack();
        BlockState blockState2 = world.getBlockState(blockPos);
        final Block block = blockState2.getBlock();
        if (block == blockState.getBlock()) {
            blockState2 = this.placeFromTag(blockPos, world, itemStack, blockState2);
            this.postPlacement(blockPos, world, playerEntity, itemStack, blockState2);
            block.onPlaced(world, blockPos, blockState2, playerEntity, itemStack);
            if (playerEntity instanceof ServerPlayerEntity) {
                Criteria.PLACED_BLOCK.trigger((ServerPlayerEntity) playerEntity, blockPos, itemStack);
            }
        }
        final BlockSoundGroup blockSoundGroup = blockState2.getSoundGroup();
        world.playSound(playerEntity, blockPos, this.getPlaceSound(blockState2), SoundCategory.BLOCKS,
            (blockSoundGroup.getVolume() + 1.0f) / 2.0f, blockSoundGroup.getPitch() * 0.8f);
        if (playerEntity == null || !playerEntity.abilities.creativeMode) {
            itemStack.decrement(1);
        }
        return ActionResult.success(world.isClient);
    }

    @Shadow
    protected abstract SoundEvent getPlaceSound(BlockState blockState);

    @Nullable
    @Shadow
    public abstract ItemPlacementContext getPlacementContext(ItemPlacementContext itemPlacementContext);

    @Shadow
    protected abstract boolean postPlacement(BlockPos blockPos, World world, @Nullable PlayerEntity playerEntity,
        ItemStack itemStack, BlockState blockState);

    @Nullable
    @Shadow
    protected abstract BlockState getPlacementState(ItemPlacementContext itemPlacementContext);

    @Shadow
    private BlockState placeFromTag(
        final BlockPos blockPos, final World world, final ItemStack itemStack, final BlockState blockState) {
        return null;
    }

    @Shadow
    protected abstract boolean place(ItemPlacementContext itemPlacementContext, BlockState blockState);
}
