package org.valkyrienskies.mod.mixin.feature.ship_aware_level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.util.ShipAwareCollisionUtil;
import org.valkyrienskies.mod.common.util.VanillaLevelDefaults;

// Ship blocks count as roof for canSeeSky and as obstruction for noCollision, for every caller.
// Overrides rather than interface injectors: stock Forge mixin can't inject into interface default methods.
// The vanilla defaults are reached through VanillaLevelDefaults.
@Mixin(Level.class)
public abstract class MixinLevel implements VanillaLevelDefaults {

    @Override
    public boolean canSeeSky(final BlockPos pos) {
        return vs$vanillaCanSeeSky(pos) && CompatUtil.INSTANCE.noShipRoofAbove(Level.class.cast(this), pos);
    }

    // The other noCollision overloads delegate here.
    @Override
    public boolean noCollision(@Nullable final Entity entity, final AABB aabb) {
        return vs$vanillaNoCollision(entity, aabb)
            && ShipAwareCollisionUtil.noShipCollision(Level.class.cast(this), entity, aabb);
    }
}
