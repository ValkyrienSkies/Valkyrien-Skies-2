package org.valkyrienskies.mod.compat.flywheel;

import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FlywheelDynLightCompat {
    public static void updateDynamicLightingForFlywheel(ClientLevel level) {
        VisualizationManager manager = VisualizationManager.get(level);
        if (manager != null) {
            VisualManagerImpl<BlockEntity, BlockEntityStorage> blockEntityManager = (VisualManagerImpl<BlockEntity, BlockEntityStorage>) manager.blockEntities();
            if (VSGameConfig.CLIENT.getDynamicShipLighting()) {
                for (Long sectionLong : ShipEmbeddingManager.INSTANCE.sectionsWithBlockEntities()) {
                    blockEntityManager.onLightUpdate(sectionLong);
                }
            }
            if (VSGameConfig.CLIENT.getDynamicShipToWorldLighting()) {
                for (Long sectionLong : SodiumCompat.getWorldFromShipStorage().trackedSections()) {
                    blockEntityManager.onLightUpdate(sectionLong);
                }
            }
        }
    }
}