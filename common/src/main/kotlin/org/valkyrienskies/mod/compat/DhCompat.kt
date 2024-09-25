package org.valkyrienskies.mod.compat

import com.seibel.distanthorizons.core.api.external.methods.config.DhApiConfig
import com.seibel.distanthorizons.core.pos.DhSectionPos
import net.minecraft.client.Minecraft
import net.minecraft.world.level.ChunkPos
import org.valkyrienskies.mod.common.centeredBlockPosToWorldChunkPos

object DhCompat {

    @JvmStatic
    fun dhViewDistance() =
        DhApiConfig.INSTANCE.graphics().chunkRenderDistance().value

    @JvmStatic
    fun toWorld(dhSectionPos: Long): ChunkPos {
        val bx = DhSectionPos.getCenterBlockPosX(dhSectionPos)
        val bz = DhSectionPos.getCenterBlockPosZ(dhSectionPos)

        val level = Minecraft.getInstance().level
        return level.centeredBlockPosToWorldChunkPos(bx, bz)
    }
}
