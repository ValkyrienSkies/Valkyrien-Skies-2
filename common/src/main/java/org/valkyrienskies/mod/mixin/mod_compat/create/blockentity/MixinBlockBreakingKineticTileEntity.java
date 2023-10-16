package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.CreateCompat;

@Mixin(BlockBreakingKineticBlockEntity.class)
public abstract class MixinBlockBreakingKineticTileEntity extends BlockEntity implements CreateCompat.HarvesterBlockEntity {

    public MixinBlockBreakingKineticTileEntity() {
        super(null, null, null);
        throw new IllegalStateException("MixinBlockBreakingKineticTileEntity ctor called");
    }

    @Shadow
    protected abstract BlockPos getBreakingPos();

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void tick(final CallbackInfo ci) {
        if (this.level.isClientSide && CreateCompat.shouldRenderHarvesterBoxes())
            CreateCompat.getClientHarvesters().add(this.worldPosition.asLong());
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/base/BlockBreakingKineticBlockEntity;getBreakingPos()Lnet/minecraft/core/BlockPos;"
            ), remap = false
    )
    private BlockPos getBreakingBlockPos(final BlockBreakingKineticBlockEntity self) {
        if (VSGameConfig.COMMON.COMPAT.getEnableHarvestingZone()) {
            return valkyrienskies$getBreakingBlockPosWithZone(self);
        } else {
            return valkyrienskies$getBreakingBlockPosWithClip(self);
        }
    }

    @Unique
    private BlockPos valkyrienskies$getBreakingBlockPosWithZone(BlockBreakingKineticBlockEntity self) {
        var result = VSGameUtilsKt.getShipsIntersecting(this.level, this.getHitAABB()).iterator();
        if (!result.hasNext()) return this.getBreakingPos();

        final Ship myShip = VSGameUtilsKt.getShipManagingPos(this.level, this.getBlockPos());

        // just something farther away
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        while (result.hasNext()) {
            double dist;
            final Ship ship = result.next();
            if (ship == myShip) continue;
            final var inShip = ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOMLD(this.getBlockPos()).add(0.5, 0.5, 0.5));
            final BlockPos pos = new BlockPos(inShip.x + 0.5, inShip.y + 0.5, inShip.z + 0.5);

            if (!level.getBlockState(pos).isAir()) {
                dist = VectorConversionsMCKt.toJOMLD(pos)
                    .add(0.5, 0.5, 0.5).distanceSquared(inShip);

                if (dist < closestDist) {
                    closest = pos;
                    closestDist = dist;
                }
            }

            for (final Direction dir : Direction.values()) {
                final BlockPos offset = pos.relative(dir);
                if (!level.getBlockState(offset).isAir()) {
                    dist = VectorConversionsMCKt.toJOMLD(offset)
                        .add(0.5, 0.5, 0.5).distanceSquared(inShip);

                    if (dist < closestDist) {
                        closest = offset;
                        closestDist = dist;
                    }
                }
            }
        }

        // TODO world check

        if (myShip != null) {

        }

        if (closest == null || closestDist > (VSGameConfig.COMMON.COMPAT.getHarvestingZoneSize() + 0.3))
            return this.getBreakingPos();

        return closest;
    }

    @Unique
    private BlockPos valkyrienskies$getBreakingBlockPosWithClip(BlockBreakingKineticBlockEntity self) {
        final BlockPos orig = this.getBreakingPos();
        final Vec3 origin;
        final Vec3 target;
        final Ship ship = VSGameUtilsKt.getShipManagingPos(self.getLevel(), self.getBlockPos());

        if (ship != null) {
            origin = VectorConversionsMCKt.toMinecraft(
                ship.getShipToWorld()
                    .transformPosition(VectorConversionsMCKt.toJOMLD(self.getBlockPos()).add(0.5, 0.5, 0.5))
            );
            target = VectorConversionsMCKt.toMinecraft(
                ship.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOMLD(orig).add(0.5, 0.5, 0.5))
            );
        } else {
            origin = Vec3.atCenterOf(self.getBlockPos());
            target = Vec3.atCenterOf(orig);
        }

        final Vec3 diff = target.subtract(origin);
        final BlockHitResult result = self.getLevel().clip(new ClipContext(
            origin.add(diff.scale(0.4)),
            target.add(diff.scale(0.2)),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            null
        ));

        if (result.getType() == HitResult.Type.MISS) {
            return orig;
        }

        return result.getBlockPos();
    }

    @NotNull
    @Override
    public AABB getHitAABB() {
        if (!VSGameConfig.COMMON.COMPAT.getEnableHarvestingZone())
            throw new IllegalStateException("Harvesting zone disabled");

        final Ship ship = VSGameUtilsKt.getShipManagingPos(this.level, this.getBlockPos());
        if (ship == null)
            return new AABB(this.getBlockPos()).inflate((VSGameConfig.COMMON.COMPAT.getHarvestingZoneSize() - 1) / 2);

        final var worldPos = ship.getShipToWorld().transformPosition(
            VectorConversionsMCKt.toJOMLD(this.getBlockPos()).add(0.5, 0.5, 0.5)
        );

        return new AABB(
            worldPos.x + 0.5, worldPos.y + 0.5, worldPos.z + 0.5,
            worldPos.x - 0.5, worldPos.y - 0.5, worldPos.z - 0.5
        ).inflate((VSGameConfig.COMMON.COMPAT.getHarvestingZoneSize() - 1) / 2);
    }
}

