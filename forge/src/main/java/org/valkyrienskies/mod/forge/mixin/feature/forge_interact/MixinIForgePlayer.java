package org.valkyrienskies.mod.forge.mixin.feature.forge_interact;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.extensions.IForgePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(IForgePlayer.class)
public interface MixinIForgePlayer {

    @Shadow
    Player self();

    /**
     * Include ships in server-side distance check when player interacts with a block.
     */
    @Overwrite(remap = false)
    default boolean canReach(final BlockPos pos, final double padding) {
        if (VSGameConfig.SERVER.getEnableInteractDistanceChecks()) {
            final double reach = this.self().getEntityReach() + padding;
            final Vec3 eyes = this.self().getEyePosition();
            return VSGameUtilsKt.squaredDistanceBetweenInclShips(this.self().level(),
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                eyes.x, eyes.y, eyes.z
            ) < reach * reach;
        } else {
            return true;
        }
    }

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
