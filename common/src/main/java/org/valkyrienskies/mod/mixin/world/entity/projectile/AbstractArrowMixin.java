package org.valkyrienskies.mod.mixin.world.entity.projectile;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin extends Entity {
    @Unique
    private static boolean isModifyingHit = false;
    @Shadow
    protected boolean inGround;
    @Shadow
    protected int inGroundTime;
    @Shadow
    public int shakeTime;

    public AbstractArrowMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickMixin(final CallbackInfo ci) {
        if (isModifyingHit) {
            return;
        }
        isModifyingHit = true;
        final boolean bl = this.isNoPhysics();
        final Vec3 oldPos = this.position();
        final double origX = oldPos.x();
        final double origY = oldPos.y();
        final double origZ = oldPos.z();

        VSGameUtilsKt.transformToNearbyShipsAndWorld(this.level, origX, origY, origZ,
            this.getBoundingBox().getSize(), (x, y, z) -> {
                this.setPos(x, y, z);
                final Vec3 vec3 = this.getDeltaMovement();
                final BlockPos blockPos = new BlockPos(x, y, z);
                final BlockState blockState = this.level.getBlockState(blockPos);
                Vec3 vec32;
                if (!blockState.isAir() && !bl) {
                    final VoxelShape voxelShape = blockState.getCollisionShape(this.level, blockPos);
                    if (!voxelShape.isEmpty()) {
                        vec32 = this.position();
                        final Iterator var7 = voxelShape.toAabbs().iterator();

                        while (var7.hasNext()) {
                            final AABB aABB = (AABB) var7.next();
                            if (aABB.move(blockPos).contains(vec32)) {
                                this.inGround = true;
                                break;
                            }
                        }
                    }
                }

                if (this.shakeTime > 0) {
                    --this.shakeTime;
                }

                if (this.isInWaterOrRain()) {
                    this.clearFire();
                }

                if (!(this.inGround && !bl)) {
                    this.inGroundTime = 0;
                    final Vec3 vec33 = this.position();
                    vec32 = vec33.add(vec3);
                    final HitResult hitResult = this.level.clip(
                        new ClipContext(vec33, vec32, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

                    if (!this.removed) {

                        if (hitResult.getType() == HitResult.Type.BLOCK && !bl) {
                            this.onHitBlock((BlockHitResult) hitResult);
                            this.hasImpulse = true;
                        }

                    }
                }
                this.setPos(origX, origY, origZ);
            });
        isModifyingHit = false;

    }

    @Shadow
    protected void onHitBlock(final BlockHitResult result) {
        this.onHitBlock(result);
    }

    @Shadow
    private boolean isNoPhysics() {
        return this.isNoPhysics();
    }

}
