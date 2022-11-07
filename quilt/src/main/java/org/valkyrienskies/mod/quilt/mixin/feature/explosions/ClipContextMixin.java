package org.valkyrienskies.mod.quilt.mixin.feature.explosions;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClipContext.class)
public class ClipContextMixin {
    @Shadow
    @Final
    private CollisionContext collisionContext;

    //In features like explosion push force, there is not always a relevant entity for a clip to use.
    //CollisionContext.of in vanilla requires a non-null entity. This mixin makes parity with the Forge implementation of CollisionContext.
    @Redirect(method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/CollisionContext;of(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/shapes/CollisionContext;"
        ))
    public CollisionContext collisionContextRedirect(final Entity entity) {
        if (entity == null) {
            return CollisionContext.empty();
        }
        return CollisionContext.of(entity);
    }
}
