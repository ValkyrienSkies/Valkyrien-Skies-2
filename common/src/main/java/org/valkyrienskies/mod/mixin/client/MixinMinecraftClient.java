package org.valkyrienskies.mod.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.mixinducks.client.MinecraftClientDuck;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient implements MinecraftClientDuck {

    @Unique
    private HitResult originalCrosshairTarget;

    @Override
    public void vs$setOriginalCrosshairTarget(final HitResult originalCrosshairTarget) {
        this.originalCrosshairTarget = originalCrosshairTarget;
    }

    @Override
    public HitResult vs$getOriginalCrosshairTarget() {
        return originalCrosshairTarget;
    }

    @Redirect(
        method = "doItemUse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;"
        )
    )
    private ActionResult makeSlabsWork(final ClientPlayerInteractionManager receiver,
        final ClientPlayerEntity player, final ClientWorld world, final Hand hand, final BlockHitResult hitResult) {
        return receiver.interactBlock(player, world, hand, (BlockHitResult) originalCrosshairTarget);
    }
}
