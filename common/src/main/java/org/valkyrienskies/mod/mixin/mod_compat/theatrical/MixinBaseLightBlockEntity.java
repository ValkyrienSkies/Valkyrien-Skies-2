package org.valkyrienskies.mod.mixin.mod_compat.theatrical;

import dev.imabad.theatrical.blockentities.light.BaseLightBlockEntity;
import dev.imabad.theatrical.blockentities.light.LightCollisionContext;
import dev.imabad.theatrical.mixin.ClipContextAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(BaseLightBlockEntity.class)
public abstract class MixinBaseLightBlockEntity extends BlockEntity {

    public MixinBaseLightBlockEntity(BlockEntityType<?> blockEntityType,
        BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Shadow
    public static Vec3 rayTraceDir(BaseLightBlockEntity be) { return null; }

    @Shadow
    public float getMaxLightDistance() { return 0.0f; }

    @Shadow
    private BlockPos emissionBlock;

    /**
     * @author brickyboy
     * @reason no one else is mixining this very niche and archived mod, and this makes our mixin a LOT cleaner
     */
    @Overwrite(remap = false)
    public double doRayTrace() {
        Vec3 viewVector = rayTraceDir((BaseLightBlockEntity) (Object) this);
        double distance = this.getMaxLightDistance();

        Vec3 originCenter = this.getBlockPos().getCenter();

        // Do our raycasts in worldspace, but with the ability to hit ships
        originCenter = VSGameUtilsKt.toWorldCoordinates(this.level, originCenter);
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, this.getBlockPos());
        if (ship != null) {
            viewVector = VectorConversionsMCKt.toMinecraft(ship.getShipToWorld().transformDirection(VectorConversionsMCKt.toJOML(viewVector)));
        }

        Vec3 clipEnd = originCenter.add(viewVector.x * distance, viewVector.y * distance, viewVector.z * distance);
        ClipContext context = new ClipContext(originCenter, clipEnd, Block.COLLIDER, Fluid.NONE, null);

        ((ClipContextAccessor)context).setCollisionContext(new LightCollisionContext(this.getBlockPos()));
        BlockHitResult result = RaycastUtilsKt.clipIncludeShips(this.level, context);

        // Light pos will be in world space if we hit world, or our OWN ship space if clipWithShips hit ourselves (or another ship)
        BlockPos lightPos = result.getBlockPos();
        if (result.getType() != Type.MISS && !result.isInside()) {
            // Do distance check with both points inworld space
            if (!result.getBlockPos().equals(this.getBlockPos())) {
                lightPos = result.getBlockPos().relative(result.getDirection(), 1);
            }
        }

        distance = VSGameUtilsKt.toWorldCoordinates(this.level, result.getLocation()).distanceTo(originCenter);
        this.emissionBlock = lightPos;
        return distance;
    }

}
