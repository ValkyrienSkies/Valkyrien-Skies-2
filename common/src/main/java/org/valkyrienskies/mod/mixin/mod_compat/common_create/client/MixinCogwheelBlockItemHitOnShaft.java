package org.valkyrienskies.mod.mixin.mod_compat.common_create.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(targets = {
        "com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem$DiagonalCogHelper"
})
public class MixinCogwheelBlockItemHitOnShaft {
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/BlockHitResult;getLocation()Lnet/minecraft/world/phys/Vec3;"), require = 0)
    private Vec3 redirectGetLocation(BlockHitResult instance) {
        Vec3 result = instance.getLocation();
        Level world = Minecraft.getInstance().level;
        if(world != null) {
            result = CompatUtil.INSTANCE.toSameSpaceAs(world, result, instance.getBlockPos());
        }
        return result;
    }
}
