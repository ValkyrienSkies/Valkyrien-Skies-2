package org.valkyrienskies.mod.mixin.client;

import com.mojang.datafixers.util.Function4;
import java.util.function.Function;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.PlayerUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.event.RegistryEvents;
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
    private ActionResult wrapInteractBlock(final ClientPlayerInteractionManager receiver,
        final ClientPlayerEntity player, final ClientWorld world, final Hand hand, final BlockHitResult hitResult) {

        return PlayerUtil.INSTANCE.transformPlayerTemporarily(player,
            VSGameUtilsKt.getShipObjectManagingPos(world, hitResult.getBlockPos()),
            () -> receiver.interactBlock(player, world, hand, (BlockHitResult) originalCrosshairTarget));
    }

    @Inject(
        method = "startIntegratedServer(Ljava/lang/String;Lnet/minecraft/util/registry/DynamicRegistryManager$Impl;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/client/MinecraftClient$WorldLoadAction;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/resource/ServerResourceManager;loadRegistryTags()V",
            shift = At.Shift.AFTER
        )
    )
    private void afterTags(final String string, final DynamicRegistryManager.Impl impl,
        final Function<LevelStorage.Session, DataPackSettings> function,
        final Function4<LevelStorage.Session, DynamicRegistryManager.Impl, ResourceManager, DataPackSettings, SaveProperties> function4,
        final boolean bl, final MinecraftClient.WorldLoadAction worldLoadAction, final CallbackInfo ci) {

        RegistryEvents.tagsAreLoaded();
    }
}
