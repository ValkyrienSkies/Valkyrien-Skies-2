package org.valkyrienskies.mod.mixin.client.gui.hud;

import static net.minecraft.client.gui.DrawableHelper.fill;

import com.google.common.base.Strings;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        RenderSystem.pushMatrix();

        final TextRenderer fontRenderer = client.textRenderer;
        final String string = "VS 2 Alpha Build";
        if (!Strings.isNullOrEmpty(string)) {
            final int j = 9;
            final int k = fontRenderer.getWidth(string);
            final int m = 20;

            final int xMin = 1;

            fill(matrices, xMin, m - 1, 2 + k + xMin, m + j - 1, -1873784752);
            fontRenderer.draw(matrices, string, 2.0F, (float) m, 14737632);
        }

        RenderSystem.popMatrix();
    }
}
