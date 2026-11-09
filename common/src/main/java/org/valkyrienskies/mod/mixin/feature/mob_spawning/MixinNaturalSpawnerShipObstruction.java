package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipAwareCollisionUtil;

// Backstop for natural-spawn: Mob.checkSpawnObstruction overrides that don't call super
// (Drowned, WaterAnimal, IronGolem, Ocelot, Axolotl, Guardian, Strider, ZombifiedPiglin,
// MagmaCube, Ravager) bypass MixinMobSpawnObstruction's RETURN inject.
@Mixin(NaturalSpawner.class)
public abstract class MixinNaturalSpawnerShipObstruction {

    @ModifyReturnValue(method = "isValidPositionForMob", at = @At("RETURN"))
    private static boolean vs$shipAwareObstruction(
        final boolean original,
        final ServerLevel level, final Mob mob, final double distSqr
    ) {
        if (!original) return false;
        return ShipAwareCollisionUtil.noCollisionIncludingShips(level, mob, mob.getBoundingBox());
    }
}
