package org.valkyrienskies.mod.forge.mixin.compat.mixinbooster;

import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.extensions.IForgePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

/**
 * A variant of {@link org.valkyrienskies.mod.forge.mixin.feature.forge_interact.MixinIForgePlayer} that uses
 * injectors to be more compatible with other mods
 */
@Mixin(IForgePlayer.class)
@Pseudo
public interface MixinIForgePlayer {

    @Shadow
    Player self();

    @Inject(
        method = "isCloseEnough(Lnet/minecraft/world/entity/Entity;D)Z",
        at = @At(value = "HEAD"),
        cancellable = true,
        remap = false
    )
    default void preIsCloseEnough(final Entity entity, final double distance, final CallbackInfoReturnable<Double> cir) {
        if (VSGameConfig.SERVER.getEnableInteractDistanceChecks() &&
            VSGameUtilsKt.isBlockInShipyard(entity.level(), entity.blockPosition())) {
            final Vec3 eye = this.self().getEyePosition();
            final Vec3 targetCenter = entity.getPosition(1.0F).add(0.0, entity.getBbHeight() / 2.0F, 0.0);
            final Optional<Vec3> hit = entity.getBoundingBox().clip(eye, targetCenter);
            hit.ifPresent(vec3 -> cir.setReturnValue(VSGameUtilsKt.squaredDistanceBetweenInclShips(this.self().level(),
                vec3.x, vec3.y, vec3.z, eye.x, eye.y, eye.z)));
        }
    }
}
