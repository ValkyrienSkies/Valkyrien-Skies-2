package org.valkyrienskies.mod.mixin.feature.air_pockets.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

/**
 * Renders shipyard-contained water with the biome color of the ship's world position.
 *
 * <p>Ship blocks live in the shipyard coordinate space, so vanilla biome tint sampling uses the shipyard biome, which
 * doesn't match the actual biome the ship is currently in.
 *
 * <p>Shipyard chunk meshes are baked once, but ships move. So we bake ship water with a neutral tint here and apply the
 * real world-biome tint at render time via shader uniforms.
 */
@Mixin(BiomeColors.class)
public abstract class MixinBiomeColors {

    @Unique
    private static final ThreadLocal<Boolean> valkyrienair$recursing =
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "getAverageWaterColor", at = @At("HEAD"), cancellable = true)
    private static void valkyrienair$shipyardWaterUsesWorldBiomeTint(final BlockAndTintGetter blockAndTintGetter,
        final BlockPos shipyardBlockPos, final CallbackInfoReturnable<Integer> cir) {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return;
        if (valkyrienair$recursing.get()) return;

        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        if (!VSGameUtilsKt.isBlockInShipyard(level, shipyardBlockPos)) return;

        try {
            valkyrienair$recursing.set(Boolean.TRUE);
            // Shipyard chunk meshes are baked once, but ships move through the world.
            // Returning a neutral tint here lets us apply the *current* world-biome tint at render time via shader.
            cir.setReturnValue(0xFFFFFF);
        } finally {
            valkyrienair$recursing.set(Boolean.FALSE);
        }
    }
}
