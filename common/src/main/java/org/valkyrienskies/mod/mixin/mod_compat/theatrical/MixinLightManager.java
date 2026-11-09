package org.valkyrienskies.mod.mixin.mod_compat.theatrical;

import dev.imabad.theatrical.blockentities.light.BaseLightBlockEntity;
import dev.imabad.theatrical.compat.ModCompat;
import dev.imabad.theatrical.compat.ShimmerCompat;
import dev.imabad.theatrical.lighting.LightManager;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LightManager.class)
public abstract class MixinLightManager {

    @Shadow
    private static void theatricalLightHandler(BaseLightBlockEntity light, LevelRenderer renderer, int luminance, BlockPos emissionBlock) {}

    /**
     * @author brickyboy
     * @reason no one else is mixining this very niche and archived mod, and this makes our mixin a LOT cleaner
     */
    //TODO: fix this mixin. It works, in the sense that the values are changed correctly,
    // but the light still refuses to make all world+ship spaces bright at the same time. One always takes priority.
    // BE is overriding it is my best guesss
    /*@Overwrite(remap = false)
    public static boolean updateDynamicLight(BaseLightBlockEntity light, LevelRenderer renderer) {
        int luminance = light.getLightLuminance();
        float spread = light.getLightSpread();

        AtomicBoolean didAnUpdate = new AtomicBoolean(false);

        AABB blockAABB = AABB.ofSize(VSGameUtilsKt.toWorldCoordinates(light.getLevel(), Vec3.atLowerCornerOf(light.getEmissionBlock())), 1, 1, 1);
        VSGameUtilsKt.transformFromWorldToNearbyShipsAndWorld(light.getLevel(), blockAABB, (aabb -> {

            BlockPos emissionBlock = BlockPos.containing(aabb.getCenter());

            if (emissionBlock.equals(light.getPrevEmissionBlock()) && luminance == light.getPrevLuminance() && spread == light.getPrevSpread()) {
                if (ModCompat.SHIMMER && (light.getPrevColour() != light.getLightColour() || light.getPrevSpread() != light.getLightSpread())) {
                    light.setPrevColour(light.getLightColour());
                    light.setPrevSpread(light.getLightSpread());
                    ShimmerCompat.handleLightUpdate(light);
                }

            } else {
                light.setPrevEmissionBlock(emissionBlock);
                light.setPrevLuminance(luminance);
                light.setPrevSpread(spread);
                if (ModCompat.SHIMMER) {
                    ShimmerCompat.handleLightUpdate(light);
                } else {
                    theatricalLightHandler(light, renderer, luminance, emissionBlock);
                }

                didAnUpdate.set(true);
            }
        }));

        return didAnUpdate.get();
    }*/
}
