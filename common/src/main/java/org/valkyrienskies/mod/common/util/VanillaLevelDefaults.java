package org.valkyrienskies.mod.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Reaches the vanilla interface defaults that MixinLevel overrides. A mixin can't write Interface.super.method()
 * itself (Mixin rewrites the owner and the JVM rejects the call), but a normal interface extending them can.
 */
public interface VanillaLevelDefaults extends BlockAndTintGetter, CollisionGetter {
    default boolean vs$vanillaCanSeeSky(final BlockPos pos) {
        return BlockAndTintGetter.super.canSeeSky(pos);
    }

    default boolean vs$vanillaNoCollision(@Nullable final Entity entity, final AABB aabb) {
        return CollisionGetter.super.noCollision(entity, aabb);
    }
}
