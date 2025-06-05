package org.valkyrienskies.mod.fabric.mixin.compat.create.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(targets = {
        "com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem$SmallCogHelper",
        "com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem$LargeCogHelper",
        "com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem$DiagonalCogHelper",
        "com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem$IntegratedLargeCogHelper",
        "com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem$IntegratedSmallCogHelper"
})
public class MixinCogwheelBlockItem {
    @Redirect(method = "getOffset", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/BlockHitResult;getLocation()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectGetLocation(BlockHitResult instance) {
        Vec3 result = instance.getLocation();
        Level world = Minecraft.getInstance().level;
        if (world != null) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(world, instance.getBlockPos());
            if (ship != null && !VSGameUtilsKt.isBlockInShipyard(world, result.x, result.y, result.z)) {
                Vector3d tempVec = VectorConversionsMCKt.toJOML(result);
                ship.getWorldToShip().transformPosition(tempVec, tempVec);
                result = VectorConversionsMCKt.toMinecraft(tempVec);
            }
        }
        return result;
    }
}
