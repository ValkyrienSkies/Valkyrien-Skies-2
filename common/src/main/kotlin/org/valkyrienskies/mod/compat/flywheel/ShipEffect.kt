package org.valkyrienskies.mod.compat.flywheel

import com.simibubi.create.content.trains.track.TrackBlockOutline.result
import dev.engine_room.flywheel.api.visual.Effect
import dev.engine_room.flywheel.api.visual.EffectVisual
import dev.engine_room.flywheel.api.visualization.VisualManager
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper
import it.unimi.dsi.fastutil.longs.LongArraySet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import it.unimi.dsi.fastutil.longs.LongSets
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.SectionPos
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.entity.BlockEntity
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.util.logger
import java.util.WeakHashMap

class ShipEffect(val ship: ClientShip, val level: ClientLevel) : Effect {
    init {
        map[ship] = this
    }

    internal var manager: VisualManager<BlockEntity>? = null
    private var dirtySections = LongSets.synchronize(LongOpenHashSet())

    fun queueAddition(blockEntity: BlockEntity) {
        manager?.queueAdd(blockEntity)
    }

    fun queueUpdate(blockEntity: BlockEntity) {
        manager?.queueUpdate(blockEntity)
    }

    fun queueRemoval(blockEntity: BlockEntity) {
        manager?.queueRemove(blockEntity)
    }

    fun setDirty(sectionX: Int, sectionY: Int, sectionZ: Int, important: Boolean = false) {
        dirtySections.add(SectionPos.asLong(sectionX, sectionY, sectionZ))
    }

    override fun level(): LevelAccessor = level

    override fun visualize(ctx: VisualizationContext, partialTick: Float): EffectVisual<ShipEffect> =
        EmbeddingShipVisual(this, ctx).apply { update(partialTick) }

    internal fun pullQueuedSections(): LongSet {
        val r = LongArraySet()
        r.addAll(dirtySections)
        dirtySections.clear()
        return LongSets.unmodifiable(r)
    }

    internal fun areSectionsDirty(): Boolean {
        synchronized(dirtySections) {
            return !dirtySections.isEmpty()
        }
    }

    companion object {
        private val map = WeakHashMap<ClientShip, ShipEffect>()
        private val logger by logger("ShipEffect-Flywheel")

        fun getShipEffect(ship: ClientShip): ShipEffect = map.getOrPut(ship) {
            ShipEffect(ship, Minecraft.getInstance().level!!).apply {
                logger.warn("Added dynamically a ship effect, shouldn't happen.")
                VisualizationHelper.queueAdd(this)
            }
        }
    }
}
