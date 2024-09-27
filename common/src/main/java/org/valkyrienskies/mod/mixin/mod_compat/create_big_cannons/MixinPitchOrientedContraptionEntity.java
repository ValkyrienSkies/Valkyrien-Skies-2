package org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;

import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity")
public abstract class MixinPitchOrientedContraptionEntity extends OrientedContraptionEntity {

    @Shadow
    private BlockPos controllerPos;

    public MixinPitchOrientedContraptionEntity(final EntityType<?> type,
        final Level world) {
        super(type, world);
    }

    @Inject(method = "processRiderPositionHook", at = @At("HEAD"), cancellable = true, remap = false)
    protected void vsProcesssRiderPositionHook(
        final Entity passenger, @Nullable Vec3 original, final CallbackInfoReturnable<Vec3> ci) {

        if (original != null && controllerPos != null) {
            final Vector3d editOriginal = VectorConversionsMCKt.toJOML(original);

            final Ship ship = VSGameUtilsKt.getShipObjectManagingPos(level(), controllerPos);

            if (ship != null) {
                ship.getShipToWorld().transformPosition(editOriginal);

                original = VectorConversionsMCKt.toMinecraft(editOriginal).add(0.5, 1, 0.5)
                    .subtract(0, passenger.getEyeHeight(), 0);
                ci.setReturnValue(original);
            }
        }
    }

    @Inject(method="getPassengerPosition", at = @At("RETURN"), cancellable = true, remap = false)
    protected void vsGetPassengerPosition(Entity passenger, float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (VSGameUtilsKt.getShipObjectManagingPos(passenger.level(), toJOML(this.position())) != null) {
            cir.setReturnValue(cir.getReturnValue().add(0,0.1,0));
        }
    }
}
