package org.valkyrienskies.mod.compat

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import org.valkyrienskies.mod.common.config.VSGameConfig

object CreateCompat {

    private val contraptionClass = runCatching {
        Class.forName("com.simibubi.create.content.contraptions.AbstractContraptionEntity")
    }.getOrNull()

    @JvmStatic
    fun isContraption(entity: Entity): Boolean {
        return contraptionClass?.isInstance(entity) ?: false
    }

    @JvmStatic
    fun shouldRenderHarvesterBoxes(): Boolean =
        Minecraft.getInstance().entityRenderDispatcher.shouldRenderHitBoxes() &&
            VSGameConfig.CLIENT.COMPAT.showHarvestingZone &&
            VSGameConfig.COMMON.COMPAT.enableHarvestingZone

    @JvmStatic
    var clientHarvesters: LongSet = LongOpenHashSet()

    interface HarvesterBlockEntity {
        val hitAABB: AABB
    }
}
