package org.valkyrienskies.mod.mixin.client.renderer;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.render.MeshCompileLightProbe;

/**
 * Test-only hook: captures {@link LevelRenderer#getLightColor(BlockAndTintGetter, BlockState, BlockPos)}
 * return values when {@link MeshCompileLightProbe} is armed on the current thread.
 *
 * <p>During section compile, every rendered block face ultimately packs its
 * per-vertex light via this static method — it reads
 * {@code getBrightness(SKY, pos)} and {@code getBrightness(BLOCK, pos)} from
 * the {@link BlockAndTintGetter} (which, for ship sections, is a
 * {@code RenderChunkRegion} delegating to the live
 * {@link net.minecraft.world.level.lighting.LevelLightEngine}). Capturing
 * the packed result tells us exactly what light the mesh baked for each
 * face position.
 *
 * <p>Production overhead is a single {@code ARMED.get() != null} check per
 * face lighting pack, which is effectively a volatile read of a ThreadLocal —
 * cheap enough that keeping this in the main mixin set is fine.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererLightColorProbe {

    @ModifyReturnValue(
        method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
        at = @At("RETURN")
    )
    private static int vs$captureMeshLight(final int packed,
                                           final BlockAndTintGetter region,
                                           final BlockState state,
                                           final BlockPos pos) {
        if (MeshCompileLightProbe.isArmed()) {
            MeshCompileLightProbe.capture(pos, packed);
        }
        return packed;
    }
}
