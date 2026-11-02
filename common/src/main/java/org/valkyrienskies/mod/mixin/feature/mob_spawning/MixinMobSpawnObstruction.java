package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.util.ShipAwareCollisionUtil;

@Mixin(Mob.class)
public abstract class MixinMobSpawnObstruction {

    @Inject(method = "checkSpawnObstruction", at = @At("RETURN"), cancellable = true)
    private void vs$shipAwareSpawnObstruction(
        final LevelReader level,
        final CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValueZ() || !(level instanceof Level fullLevel)) return;
        final Mob self = (Mob) (Object) this;
        if (!ShipAwareCollisionUtil.noCollisionIncludingShips(fullLevel, self, self.getBoundingBox())) {
            cir.setReturnValue(false);
        }
    }
}
