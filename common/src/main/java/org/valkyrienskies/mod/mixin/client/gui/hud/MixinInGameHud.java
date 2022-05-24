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
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.VSOptions;
import org.valkyrienskies.core.pipelines.VSPipeline;

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
            String physicsTPS = "Error";
            try {
                // This is dangerous because we have to reach into the Server state from the Client, which can fail.
                // So, put this in a try/catch block to catch any errors that may occur.
                physicsTPS = " " + VSPipeline.Companion.getVSPipeline().computePhysTps();
            } catch (final Exception e) {
                e.printStackTrace();
            }
            final String worldPhysicsDebugText = "VS PhysTPS: " + physicsTPS;
            debugText.add(worldPhysicsDebugText);
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
