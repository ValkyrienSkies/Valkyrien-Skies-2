package org.valkyrienskies.mod.mixin.feature.huge_bounding_box_fix;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.api.ValkyrienSkies;

/**
 * Transform the player's vehicle's bounding box to the shipyard, preventing the 'collision box too big' error.
 */
@Mixin(Player.class)
public abstract class MixinPlayer extends LivingEntity {

    protected MixinPlayer(final EntityType<? extends LivingEntity> entityType,
        final Level level) {
        super(entityType, level);
    }

    @ModifyExpressionValue(
        method = "aiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
        )
    )
    private AABB transformBoundingBoxToWorld(final AABB aabb) {
        return ValkyrienSkies.toWorld(this.level(), aabb);
    }
}
