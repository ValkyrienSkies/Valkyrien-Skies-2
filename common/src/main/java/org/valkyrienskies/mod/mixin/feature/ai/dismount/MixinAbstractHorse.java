package org.valkyrienskies.mod.mixin.feature.ai.dismount;

import java.util.List;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.util.ShipAwareDismount;

/**
 * Ship-aware passenger dismount for all rideable horses (Camel, Horse, Donkey, Mule, Llama,
 * SkeletonHorse, ZombieHorse). See {@link ShipAwareDismount} for the underlying logic.
 */
@Mixin(AbstractHorse.class)
public abstract class MixinAbstractHorse {

    @Inject(method = "getDismountLocationForPassenger", at = @At("HEAD"), cancellable = true)
    private void vs$shipAwareDismount(final LivingEntity passenger, final CallbackInfoReturnable<Vec3> cir) {
        final AbstractHorse mount = (AbstractHorse) (Object) this;
        final Level level = mount.level();
        if (level == null) return;

        final Ship ship = VSGameUtilsKt.getEnclosingShip(mount);
        if (ship == null) return;

        final HumanoidArm arm = passenger.getMainArm();
        final Vec3 right = ShipAwareDismount.horizontalEscape(
            mount.getBbWidth(), passenger.getBbWidth(),
            mount.getYRot() + (arm == HumanoidArm.RIGHT ? 90.0F : -90.0F)
        );
        final Vec3 left = ShipAwareDismount.horizontalEscape(
            mount.getBbWidth(), passenger.getBbWidth(),
            mount.getYRot() + (arm == HumanoidArm.LEFT ? 90.0F : -90.0F)
        );
        final double startY = mount.getBoundingBox().minY;
        final List<Vec3> candidates = List.of(
            new Vec3(mount.getX() + right.x, startY, mount.getZ() + right.z),
            new Vec3(mount.getX() + left.x, startY, mount.getZ() + left.z)
        );

        final Vec3 result = ShipAwareDismount.tryShipyardSpots(level, passenger, ship, candidates, 1);
        cir.setReturnValue(result != null ? result : mount.position());
    }

    // Inlined Entity.getCollisionHorizontalEscapeVector — it's protected.
}
