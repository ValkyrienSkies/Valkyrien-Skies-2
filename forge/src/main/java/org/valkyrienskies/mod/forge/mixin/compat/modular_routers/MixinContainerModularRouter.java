package org.valkyrienskies.mod.forge.mixin.compat.modular_routers;

import me.desht.modularrouters.block.tile.ModularRouterBlockEntity;
import me.desht.modularrouters.container.ContainerModularRouter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(ContainerModularRouter.class)
public class MixinContainerModularRouter {
    @Shadow
    @Final
    private ModularRouterBlockEntity router;

    @Redirect(method = "stillValid", at = @At(target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D", value = "INVOKE"))
    public double ValkyrienSkies$distanceCheck(final Vec3 instance, final Vec3 vec3) {
        final Level level = router.getLevel();
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, instance);
        if (ship == null)
            return instance.distanceToSqr(vec3);

        final Vector3d newInstance = ship.getTransform().getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(instance));
        return VectorConversionsMCKt.toMinecraft(newInstance).distanceToSqr(vec3);
    }
}
