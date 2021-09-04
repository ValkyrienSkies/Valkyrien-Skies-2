package org.valkyrienskies.mod.mixin.client.gui.hud;

import static net.minecraft.client.gui.DrawableHelper.fill;

import com.google.common.base.Strings;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.VSOptions;
import org.valkyrienskies.core.game.ships.ShipObjectServerWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(InGameHud.class)
public class MixinInGameHud {

    @Shadow
    @Final
    private MinecraftClient client;

    /**
     * Render the "VS 2 Alpha" text
     */
    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"))
    private void preRenderStatusEffectOverlay(final MatrixStack matrices, final CallbackInfo ci) {
        if (!VSOptions.INSTANCE.getRenderDebugText()) {
            return;
        }

        RenderSystem.pushMatrix();

        final TextRenderer fontRenderer = client.textRenderer;
        final List<String> debugText = new ArrayList<>();
        debugText.add("VS 2 Alpha Build");

        final IntegratedServer integratedServer = this.client.getServer();
        if (integratedServer != null) {
            for (final ServerWorld serverWorld : integratedServer.getWorlds()) {
                final ShipObjectServerWorld shipObjectServerWorld = VSGameUtilsKt.getShipObjectWorld(serverWorld);
                final String worldName = serverWorld.getRegistryKey().getValue().toString();
                final double physicsTPS = shipObjectServerWorld.getPhysicsTPS();
                final String worldPhysicsDebugText = worldName + " PhysTPS: " + physicsTPS;
                debugText.add(worldPhysicsDebugText);
            }
        }

        for (int i = 0; i < debugText.size(); i++) {
            final String string = debugText.get(i);
            if (!Strings.isNullOrEmpty(string)) {
                final int textHeight = 9;
                final int textLength = fontRenderer.getWidth(string);
                final int posY = 20 + i * textHeight;

                final int posX = 1;

                fill(matrices, posX, posY - 1, 2 + textLength + posX, posY + textHeight - 1, -1873784752);
                fontRenderer.draw(matrices, string, 2.0F, (float) posY, 14737632);
            }
        }

        RenderSystem.popMatrix();
    }
}
