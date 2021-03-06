package org.valkyrienskies.mod.mixin.entity;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(Entity.class)
public class MixinEntity {

    @Redirect(
        method = "raycast",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;raycast(Lnet/minecraft/world/RaycastContext;)Lnet/minecraft/util/hit/BlockHitResult;"
        )
    )
    public BlockHitResult addShipsToRaycast(final World receiver, final RaycastContext ctx) {
        return RaycastUtilsKt.raycastIncludeShips((ClientWorld) receiver, ctx);
    }

}
