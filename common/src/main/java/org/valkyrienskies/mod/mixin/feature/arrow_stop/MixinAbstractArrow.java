package org.valkyrienskies.mod.mixin.feature.arrow_stop;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(AbstractArrow.class)
public abstract class MixinAbstractArrow extends Projectile {

    @Shadow
    @Nullable
    private BlockState lastState;

    @Shadow
    protected boolean inGround;

    public MixinAbstractArrow(EntityType<? extends Projectile> entityType,
        Level level) {
        super(entityType, level);
    }

    /**
     * Stuck Arrows don't call movement method. This will forcefully update them.
     */
    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    private void pseudoMovement(CallbackInfo ci){
        if(lastState != null) {
            EntityShipCollisionUtils.INSTANCE.adjustEntityMovementForShipCollisions(this, this.getDeltaMovement(), this.getBoundingBox(), this.level());
        }
    }

    /**
     *
     */
    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState wrapBlockPosOnShip(Level level, BlockPos blockPos, Operation<BlockState> original) {
        BlockState result = original.call(level, blockPos);
        Iterable<Ship> possibleShips = VSGameUtilsKt.getShipsIntersecting(this.level(), this.getBoundingBox().inflate(0.1));
        for(Ship tempShip : possibleShips) {
            Vector3d tempVec = tempShip.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(this.position()));
            BlockPos tempBlockPos = BlockPos.containing(tempVec.x, tempVec.y, tempVec.z);
            if(!level.getBlockState(tempBlockPos).isAir()) {
                for(AABB aabb : level.getBlockState(tempBlockPos).getShape(level, tempBlockPos).toAabbs()) {
                    if(!aabb.contains(VectorConversionsMCKt.toMinecraft(tempVec))) continue;
                    inGround = true;
                    return level.getBlockState(tempBlockPos);
                }
            }
        }
        return result;
    }

    @WrapMethod(
        method = "shouldFall"
    )
    private boolean shouldFallOnShip(Operation<Boolean> original) {
        if(!original.call()) return false;
        Iterable<Ship> possibleShips = VSGameUtilsKt.getShipsIntersecting(this.level(), this.getBoundingBox().inflate(0.1));
        for(Ship ship : possibleShips) {
            AABBd aabBd = VectorConversionsMCKt.toJOML(this.getBoundingBox().inflate(0.06));
            aabBd.transform(ship.getWorldToShip());
            if(!this.level().noCollision(VectorConversionsMCKt.toMinecraft(aabBd))) {
                return false;
            }
        }
        return true;
    }
}
