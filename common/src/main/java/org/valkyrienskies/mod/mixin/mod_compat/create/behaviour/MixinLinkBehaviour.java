package org.valkyrienskies.mod.mixin.mod_compat.create.behaviour;

import com.simibubi.create.content.redstone.link.LinkBehaviour;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.bodies.properties.BodyTransform;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(LinkBehaviour.class)
public class MixinLinkBehaviour {
    @Redirect(
            method = "testHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    public Vec3 redirectSubtract(Vec3 instance, Vec3 vec) {
        Level level = ((LinkBehaviour) (Object) this).getWorld();

        Vec3 pos1 = instance;
        Vec3 pos2 = vec;

        if (level != null) {
            Ship ship1 = VSGameUtilsKt.getShipManagingPos(level, pos1.x, pos1.y, pos1.z);
            Ship ship2 = VSGameUtilsKt.getShipManagingPos(level, pos2.x, pos2.y, pos2.z);
            if (ship1 != null && ship2 == null) {
                BodyTransform transform = ship1 instanceof ClientShip cs ?
                    cs.getRenderTransform() : ship1.getTransform();
                pos2 = VectorConversionsMCKt.toMinecraft(
                    transform.getToModel().transformPosition(VectorConversionsMCKt.toJOML(pos2))
                );
            } else if (ship1 == null && ship2 != null) {
                BodyTransform transform = ship2 instanceof ClientShip cs ?
                    cs.getRenderTransform() : ship2.getTransform();
                pos1 = VectorConversionsMCKt.toMinecraft(
                    transform.getToModel().transformPosition(VectorConversionsMCKt.toJOML(pos1))
                );
            }
        }
        return pos1.subtract(pos2);
    }
}
