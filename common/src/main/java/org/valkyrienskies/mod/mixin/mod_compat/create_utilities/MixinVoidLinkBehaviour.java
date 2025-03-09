package org.valkyrienskies.mod.mixin.mod_compat.create_utilities;

/*
import me.duquee.createutilities.blocks.voidtypes.VoidLinkBehaviour;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixin.mod_compat.create.behaviour.MixinLinkBehaviour;

@Mixin(VoidLinkBehaviour.class)
public class MixinVoidLinkBehaviour {

    */
    /**
     * Identical to {@link MixinLinkBehaviour} but for Create: Utilities Void link
     */
    /*
    @Redirect(
            method = "testHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    public Vec3 redirectSubtract(Vec3 instance, Vec3 vec) {
        Level level = ((VoidLinkBehaviour) (Object) this).getWorld();

        Vec3 pos1 = instance;
        Vec3 pos2 = vec;

        if (level != null) {
            Ship ship1 = VSGameUtilsKt.getShipManagingPos(level, pos1.x, pos1.y, pos1.z);
            Ship ship2 = VSGameUtilsKt.getShipManagingPos(level, pos2.x, pos2.y, pos2.z);
            if (ship1 != null && ship2 == null) {
                pos2 = VectorConversionsMCKt.toMinecraft(
                        ship1.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(pos2))
                );
            } else if (ship1 == null && ship2 != null) {
                pos1 = VectorConversionsMCKt.toMinecraft(
                        ship2.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(pos1))
                );
            }
        }
        return pos1.subtract(pos2);
    }

}
 */
