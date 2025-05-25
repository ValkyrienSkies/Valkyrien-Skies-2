package org.valkyrienskies.mod.forge.mixin.feature.forge_interact;

import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.extensions.IPlayerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(IPlayerExtension.class)
public interface MixinIForgePlayer {

    @Shadow
    Player self();

    @Overwrite(remap = false)
    default boolean isCloseEnough(final Entity entity, final double distance) {
        if (VSGameConfig.SERVER.getEnableInteractDistanceChecks()) {
            final Vec3 eye = this.self().getEyePosition();
            final Vec3 targetCenter = entity.getPosition(1.0F).add(0.0, (double) (entity.getBbHeight() / 2.0F), 0.0);
            final Optional<Vec3> hit = entity.getBoundingBox().clip(eye, targetCenter);
            return (hit.isPresent() ?
                VSGameUtilsKt.squaredDistanceBetweenInclShips(this.self().level(),
                    hit.get().x, hit.get().y, hit.get().z, eye.x, eye.y, eye.z)
                : this.self().distanceToSqr(entity)) < distance * distance;
        } else {
            return true;
        }
    }
}
