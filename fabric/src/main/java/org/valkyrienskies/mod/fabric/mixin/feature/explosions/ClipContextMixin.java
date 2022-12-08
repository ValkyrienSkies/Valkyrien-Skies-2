package org.valkyrienskies.mod.fabric.mixin.feature.explosions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClipContext.class)
public class ClipContextMixin {

    //In features like explosion push force, there is not always a relevant entity for a clip to use.
    //CollisionContext.of in vanilla requires a non-null entity. This mixin makes parity with the Forge implementation of CollisionContext.
    @WrapOperation(method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/CollisionContext;of(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/shapes/CollisionContext;"
        ))
    public CollisionContext collisionContextRedirect(final Entity entity, final Operation<CollisionContext> operation) {
        if (entity == null) {
            return CollisionContext.empty();
        }
        return operation.call(entity);
    }
}
