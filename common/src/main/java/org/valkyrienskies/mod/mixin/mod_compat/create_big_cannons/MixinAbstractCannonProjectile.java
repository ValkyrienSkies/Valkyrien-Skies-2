package org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons;

import net.minecraft.world.phys.Vec3;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

@Mixin(AbstractCannonProjectile.class)
public abstract class MixinAbstractCannonProjectile {
    
    @Shadow
    protected abstract void clipAndDamage(Vec3 start, Vec3 end);

    @Unique
    private void shipClipAndDamage(final Vec3 startInWorld, final Vec3 endInWorld,
        final AbstractCannonProjectile projectile) {
        final AABBdc pathAABB = new AABBd(VectorConversionsMCKt.toJOML(startInWorld),
            VectorConversionsMCKt.toJOML(endInWorld)).correctBounds();

        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(projectile.level, pathAABB)) {
            final Vec3 start = VectorConversionsMCKt.toMinecraft(
                ship.getTransform().getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(startInWorld)));
            final Vec3 end = VectorConversionsMCKt.toMinecraft(
                ship.getTransform().getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(endInWorld)));
            clipAndDamage(start, end);
        }
    }

    @Inject(method = "clipAndDamage", at = @At("HEAD"), remap = false)
    protected void vsClipAndDamage(final Vec3 start, final Vec3 end, final CallbackInfo ci) {
        shipClipAndDamage(start, end, (AbstractCannonProjectile) (Object) this);
    }
}
