package org.valkyrienskies.mod.forge.mixin.compat.tis3d;

import li.cil.tis3d.common.entity.InfraredPacketEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Pseudo
@Mixin(InfraredPacketEntity.class)
public class MixinInfraredPacketEntity {
    @Shadow
    Level level;

    @ModifyVariable(remap = false, method = "checkCollision()Lnet/minecraft/world/phys/HitResult;",
        at = @At("STORE"), ordinal = 0)
    HitResult vs$checkCollision(final HitResult og, final Vec3 start, final Vec3 target) {
        return RaycastUtilsKt.clipIncludeShips(level, new CustomClipContext(start, target, null, null, null));
    }
}
