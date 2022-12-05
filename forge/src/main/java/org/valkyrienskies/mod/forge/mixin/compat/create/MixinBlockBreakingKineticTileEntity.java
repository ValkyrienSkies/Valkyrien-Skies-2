package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.simibubi.create.content.contraptions.components.actors.BlockBreakingKineticTileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(value = BlockBreakingKineticTileEntity.class, remap = false)
public abstract class MixinBlockBreakingKineticTileEntity {

    @Shadow
    protected abstract BlockPos getBreakingPos();

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/components/actors/BlockBreakingKineticTileEntity;getBreakingPos()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos getBreakingBlockPos(final BlockBreakingKineticTileEntity self) {
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

}

