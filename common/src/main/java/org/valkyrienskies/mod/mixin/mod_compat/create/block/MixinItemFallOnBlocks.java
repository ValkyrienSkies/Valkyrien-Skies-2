package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.fluids.drain.ItemDrainBlock;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlock;
import com.simibubi.create.content.logistics.chute.AbstractChuteBlock;
import com.simibubi.create.content.processing.basin.BasinBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(value = {
    MillstoneBlock.class,
    BasinBlock.class,
    AbstractChuteBlock.class,
    ItemDrainBlock.class
})
public class MixinItemFallOnBlocks extends Block {

    public MixinItemFallOnBlocks(Properties properties) {
        super(properties);
    }

    @Inject(
        method = "updateEntityAfterFallOn",
        at = @At("HEAD")
    )
    protected void findIfOnShip(BlockGetter worldIn, Entity entity, CallbackInfo ci, @Share("ship") LocalRef<Ship> ship) {
        List<Vector3d> possiblePositions = VSGameUtilsKt.transformToNearbyShipsAndWorld(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getBoundingBox().getSize());
        for (Vector3d tempPos : possiblePositions) {
            BlockPos tempBlockPos = BlockPos.containing(tempPos.x, tempPos.y, tempPos.z);
            if(worldIn.getBlockState(tempBlockPos).is(((Block)this).getClass().cast(this))
                || worldIn.getBlockState(tempBlockPos.below()).is(((Block)this).getClass().cast(this))) {
                ship.set(VSGameUtilsKt.getShipManagingPos(entity.level(), tempPos));
                break;
            }
        }
    }

    @Redirect(
            method = "updateEntityAfterFallOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"
            ),
            require = 0
    )
    protected BlockPos redirectBlockPosition(final Entity entity, @Share("ship") LocalRef<Ship> ship) {
        if (ship.get() != null) {
            Vector3d posInShip = ship.get().getTransform().getWorldToShip().transformPosition(entity.getX(), entity.getY(), entity.getZ(), new Vector3d());
            return BlockPos.containing(posInShip.x, posInShip.y, posInShip.z);
        } else return entity.blockPosition();
    }

    @Redirect(
            method = "updateEntityAfterFallOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"
            ),
            require = 0
    )
    private Vec3 redirectPosition(final Entity entity, @Share("ship") LocalRef<Ship> ship) {
        if (ship.get() != null) {
            Vector3d posInShip = ship.get().getTransform().getWorldToShip().transformPosition(entity.getX(), entity.getY(), entity.getZ(), new Vector3d());
            return VectorConversionsMCKt.toMinecraft(posInShip);
        } else return entity.position();
    }

    @Redirect(
        method = "updateEntityAfterFallOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"
        ),
        require = 0
    )
    private Vec3 redirectDeltaMovement(final Entity entity, @Share("ship") LocalRef<Ship> ship) {
        if (ship.get() != null) {
            Vector3d deltaInShip = VectorConversionsMCKt.toJOML(entity.getDeltaMovement());
            ship.get().getTransform().getWorldToShip().transformDirection(deltaInShip);
            return VectorConversionsMCKt.toMinecraft(deltaInShip);
        } else return entity.getDeltaMovement();
    }
}
