package org.valkyrienskies.mod.common.util

import net.minecraft.client.Minecraft
import org.valkyrienskies.mod.common.config.VSGameConfig

object DragInfoReporterClient {
    fun isHitboxesEnabled(): Boolean {
        if (Minecraft.getInstance().showOnlyReducedInfo()) return false
        return Minecraft.getInstance().entityRenderDispatcher.shouldRenderHitBoxes() && VSGameConfig.CLIENT.computeDragDebugInfo
    }
}
