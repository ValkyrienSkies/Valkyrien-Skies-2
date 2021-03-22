package org.valkyrienskies.mod.mixin.entity;

import java.util.Random;
import java.util.stream.Stream;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.collection.ReusableStream;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Redirect(
        method = "raycast",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;raycast(Lnet/minecraft/world/RaycastContext;)Lnet/minecraft/util/hit/BlockHitResult;"
        )
    )
    public BlockHitResult addShipsToRaycast(final World receiver, final RaycastContext ctx) {
        return RaycastUtilsKt.raycastIncludeShips((ClientWorld) receiver, ctx);
    }

    /**
     * Allow entities to collide with ships by replacing [Entity.adjustMovementForCollisions] with
     * [EntityShipCollisionUtils.adjustMovementForCollisionsAndShipCollisions].
     *
     * @author Tri0de
     */
    @Overwrite
    private Vec3d adjustMovementForCollisions(Vec3d movement) {
        final Entity thisAsEntity = Entity.class.cast(this);
        final Box box = this.getBoundingBox();
        final ShapeContext shapeContext = ShapeContext.of(thisAsEntity);
        final VoxelShape voxelShape = this.world.getWorldBorder().asVoxelShape();
        final Stream<VoxelShape> stream =
            VoxelShapes.matchesAnywhere(voxelShape, VoxelShapes.cuboid(box.contract(1.0E-7D)), BooleanBiFunction.AND)
                ? Stream.empty() : Stream.of(voxelShape);
        final Stream<VoxelShape> stream2 =
            this.world.getEntityCollisions(thisAsEntity, box.stretch(movement), (entity) -> true);
        final ReusableStream<VoxelShape> reusableStream = new ReusableStream<>(Stream.concat(stream2, stream));

        // vs code start
        final Vec3d originalMovement = movement;
        movement = EntityShipCollisionUtils.INSTANCE
            .adjustEntityMovementForShipCollisions(thisAsEntity, movement, box, this.world);

        final Vec3d vec3d = movement.lengthSquared() == 0.0D ? movement :
            adjustMovementForCollisions(thisAsEntity, movement, box, this.world, shapeContext, reusableStream);
        // vs code end

        final boolean bl = movement.x != vec3d.x;
        final boolean bl2 = movement.y != vec3d.y;
        final boolean bl3 = movement.z != vec3d.z;
        final boolean bl4 = this.onGround || bl2 && originalMovement.y < 0.0D;
        if (this.stepHeight > 0.0F && bl4 && (bl || bl3)) {
            Vec3d vec3d2 =
                adjustMovementForCollisions(thisAsEntity, new Vec3d(movement.x, this.stepHeight, movement.z),
                    box, this.world, shapeContext, reusableStream);
            final Vec3d vec3d3 = adjustMovementForCollisions(thisAsEntity, new Vec3d(0.0D, this.stepHeight, 0.0D),
                box.stretch(movement.x, 0.0D, movement.z), this.world, shapeContext, reusableStream);
            if (vec3d3.y < (double) this.stepHeight) {
                final Vec3d vec3d4 = adjustMovementForCollisions(thisAsEntity, new Vec3d(movement.x, 0.0D, movement.z),
                    box.offset(vec3d3), this.world, shapeContext, reusableStream).add(vec3d3);
                if (squaredHorizontalLength(vec3d4) > squaredHorizontalLength(vec3d2)) {
                    vec3d2 = vec3d4;
                }
            }

            if (squaredHorizontalLength(vec3d2) > squaredHorizontalLength(vec3d)) {
                return vec3d2.add(
                    adjustMovementForCollisions(thisAsEntity, new Vec3d(0.0D, -vec3d2.y + movement.y, 0.0D),
                        box.offset(vec3d2), this.world, shapeContext, reusableStream));
            }
        }

        return vec3d;
    }

    /**
     * Modify Entity.move() to remove `this.setVelocity(0.0D, vec3d2.y, vec3d2.z);` and `this.setVelocity(vec3d2.x,
     * vec3d2.y, 0.0D);`. These lines of code ruin the collision result so just remove them.
     *
     * @author Tri0de
     */
    @Overwrite
    public void move(final MovementType type, Vec3d movement) {
        final Entity thisAsEntity = Entity.class.cast(this);
        if (this.noClip) {
            this.setBoundingBox(this.getBoundingBox().offset(movement));
            this.moveToBoundingBoxCenter();
        } else {
            if (type == MovementType.PISTON) {
                movement = this.adjustMovementForPiston(movement);
                if (movement.equals(Vec3d.ZERO)) {
                    return;
                }
            }

            this.world.getProfiler().push("move");
            if (this.movementMultiplier.lengthSquared() > 1.0E-7D) {
                movement = movement.multiply(this.movementMultiplier);
                this.movementMultiplier = Vec3d.ZERO;
                this.setVelocity(Vec3d.ZERO);
            }

            movement = this.adjustMovementForSneaking(movement, type);
            final Vec3d vec3d = this.adjustMovementForCollisions(movement);
            if (vec3d.lengthSquared() > 1.0E-7D) {
                this.setBoundingBox(this.getBoundingBox().offset(vec3d));
                this.moveToBoundingBoxCenter();
            }

            this.world.getProfiler().pop();
            this.world.getProfiler().push("rest");
            this.horizontalCollision = !MathHelper.approximatelyEquals(movement.x, vec3d.x)
                || !MathHelper.approximatelyEquals(movement.z, vec3d.z);
            this.verticalCollision = movement.y != vec3d.y;
            this.onGround = this.verticalCollision && movement.y < 0.0D;
            final BlockPos blockPos = this.getLandingPos();
            final BlockState blockState = this.world.getBlockState(blockPos);
            this.fall(vec3d.y, this.onGround, blockState, blockPos);
            final Vec3d vec3d2 = this.getVelocity();
            if (movement.x != vec3d.x) {
                // this.setVelocity(0.0D, vec3d2.y, vec3d2.z);
            }

            if (movement.z != vec3d.z) {
                // this.setVelocity(vec3d2.x, vec3d2.y, 0.0D);
            }

            final Block block = blockState.getBlock();
            if (movement.y != vec3d.y) {
                block.onEntityLand(this.world, thisAsEntity);
            }

            if (this.onGround && !this.bypassesSteppingEffects()) {
                block.onSteppedOn(this.world, blockPos, thisAsEntity);
            }

            if (this.canClimb() && !this.hasVehicle()) {
                final double d = vec3d.x;
                double e = vec3d.y;
                final double f = vec3d.z;
                if (!block.isIn(BlockTags.CLIMBABLE)) {
                    e = 0.0D;
                }

                this.horizontalSpeed = (float) ((double) this.horizontalSpeed
                    + (double) MathHelper.sqrt(squaredHorizontalLength(vec3d)) * 0.6D);
                this.distanceTraveled =
                    (float) ((double) this.distanceTraveled + (double) MathHelper.sqrt(d * d + e * e + f * f) * 0.6D);
                if (this.distanceTraveled > this.nextStepSoundDistance && !blockState.isAir()) {
                    this.nextStepSoundDistance = this.calculateNextStepSoundDistance();
                    if (this.isTouchingWater()) {
                        final Entity entity =
                            this.hasPassengers() && this.getPrimaryPassenger() != null ? this.getPrimaryPassenger() :
                                thisAsEntity;
                        final float g = entity == thisAsEntity ? 0.35F : 0.4F;
                        final Vec3d vec3d3 = entity.getVelocity();
                        float h = MathHelper.sqrt(vec3d3.x * vec3d3.x * 0.20000000298023224D + vec3d3.y * vec3d3.y
                            + vec3d3.z * vec3d3.z * 0.20000000298023224D) * g;
                        if (h > 1.0F) {
                            h = 1.0F;
                        }

                        this.playSwimSound(h);
                    } else {
                        this.playStepSound(blockPos, blockState);
                    }
                } else if (this.distanceTraveled > this.nextFlySoundDistance && this.hasWings() && blockState.isAir()) {
                    this.nextFlySoundDistance = this.playFlySound(this.distanceTraveled);
                }
            }

            try {
                this.checkBlockCollision();
            } catch (final Throwable var18) {
                final CrashReport crashReport = CrashReport.create(var18, "Checking entity block collision");
                final CrashReportSection crashReportSection =
                    crashReport.addElement("Entity being checked for collision");
                this.populateCrashReport(crashReportSection);
                throw new CrashException(crashReport);
            }

            final float i = this.getVelocityMultiplier();
            this.setVelocity(this.getVelocity().multiply((double) i, 1.0D, (double) i));
            if (this.world.method_29556(this.getBoundingBox().contract(0.001D)).noneMatch((blockStatex) -> {
                return blockStatex.isIn(BlockTags.FIRE) || blockStatex.isOf(Blocks.LAVA);
            }) && this.fireTicks <= 0) {
                this.setFireTicks(-this.getBurningDuration());
            }

            if (this.isWet() && this.isOnFire()) {
                this.playSound(SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.7F,
                    1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                this.setFireTicks(-this.getBurningDuration());
            }

            this.world.getProfiler().pop();
        }
    }

    // region shadow functions and fields
    @Shadow
    public World world;
    @Shadow
    protected boolean onGround;
    @Shadow
    public float stepHeight;
    @Shadow
    public boolean noClip;
    @Shadow
    protected Vec3d movementMultiplier;
    @Shadow
    public boolean horizontalCollision;
    @Shadow
    public boolean verticalCollision;
    @Shadow
    public float horizontalSpeed;
    @Shadow
    public float distanceTraveled;
    @Shadow
    private float nextStepSoundDistance;
    @Shadow
    private float nextFlySoundDistance;
    @Shadow
    private int fireTicks;
    @Shadow
    @Final
    protected Random random;

    @Shadow
    public abstract Box getBoundingBox();

    @Shadow
    public static double squaredHorizontalLength(final Vec3d vector) {
        throw new AssertionError("Mixin failed to apply");
    }

    @Shadow
    public static Vec3d adjustMovementForCollisions(final Entity thisAsEntity, final Vec3d movement, final Box box,
        final World world,
        final ShapeContext shapeContext, final ReusableStream<VoxelShape> reusableStream) {
        return null;
    }

    @Shadow
    public abstract void setBoundingBox(Box boundingBox);

    @Shadow
    public abstract void moveToBoundingBoxCenter();

    @Shadow
    protected abstract Vec3d adjustMovementForPiston(Vec3d movement);

    @Shadow
    public abstract void setVelocity(Vec3d velocity);

    @Shadow
    protected abstract Vec3d adjustMovementForSneaking(Vec3d movement, MovementType type);

    @Shadow
    protected abstract BlockPos getLandingPos();

    @Shadow
    protected abstract void fall(double heightDifference, boolean onGround, BlockState landedState,
        BlockPos landedPosition);

    @Shadow
    public abstract Vec3d getVelocity();

    @Shadow
    public abstract void setVelocity(double x, double y, double z);

    @Shadow
    public abstract boolean bypassesSteppingEffects();

    @Shadow
    protected abstract boolean canClimb();

    @Shadow
    public abstract boolean hasVehicle();

    @Shadow
    protected abstract float calculateNextStepSoundDistance();

    @Shadow
    public abstract boolean isTouchingWater();

    @Shadow
    public abstract boolean hasPassengers();

    @Shadow
    public abstract Entity getPrimaryPassenger();

    @Shadow
    protected abstract void playSwimSound(float volume);

    @Shadow
    protected abstract void playStepSound(BlockPos pos, BlockState state);

    @Shadow
    protected abstract boolean hasWings();

    @Shadow
    protected abstract float playFlySound(float distance);

    @Shadow
    protected abstract void checkBlockCollision();

    @Shadow
    public abstract void populateCrashReport(CrashReportSection section);

    @Shadow
    protected abstract float getVelocityMultiplier();

    @Shadow
    public abstract void setFireTicks(int ticks);

    @Shadow
    protected abstract int getBurningDuration();

    @Shadow
    public abstract boolean isWet();

    @Shadow
    public abstract boolean isOnFire();

    @Shadow
    public abstract void playSound(SoundEvent sound, float volume, float pitch);
    // endregion

}
