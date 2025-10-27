package org.valkyrienskies.mod.mixin.mod_compat.create.block;

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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(value = {
        MillstoneBlock.class,
        BasinBlock.class,
        AbstractChuteBlock.class
})
public class MixinItemFallOnBlocks extends Block {

    public MixinItemFallOnBlocks(Properties properties) {
        super(properties);
    }

    @Redirect(
            method = "updateEntityAfterFallOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"
            ),
            require = 0
    )
    protected BlockPos redirectBlockPosition(final Entity entity, final BlockGetter worldIn) {
        List<Vector3d> possiblePositions = VSGameUtilsKt.transformToNearbyShipsAndWorld(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getBoundingBox().getSize());
        for (Vector3d tempPos : possiblePositions) {
            BlockPos tempBlockPos = BlockPos.containing(tempPos.x, tempPos.y, tempPos.z);
            if(worldIn.getBlockState(tempBlockPos).is(((Block)this).getClass().cast(this))
                || worldIn.getBlockState(tempBlockPos.below()).is(((Block)this).getClass().cast(this))) {
                return tempBlockPos;
            }
        }
        return entity.blockPosition();
    }

    @Redirect(
            method = "updateEntityAfterFallOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"
            ),
            require = 0
    )
    Vec3 redirectPosition(final Entity entity, final BlockGetter worldIn) {
        List<Vector3d> possiblePositions = VSGameUtilsKt.transformToNearbyShipsAndWorld(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getBoundingBox().getSize());
        for (Vector3d tempPos : possiblePositions) {
            BlockPos tempBlockPos = BlockPos.containing(tempPos.x, tempPos.y, tempPos.z);
            if(worldIn.getBlockState(tempBlockPos).is(((Block)this).getClass().cast(this))
                || worldIn.getBlockState(tempBlockPos.below()).is(((Block)this).getClass().cast(this))) {
                return VectorConversionsMCKt.toMinecraft(tempPos);
            }
        }
        return entity.position();
    }

}
