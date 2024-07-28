package org.valkyrienskies.mod.compat.flywheel

import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.instance.Instancer
import dev.engine_room.flywheel.api.model.Model
import dev.engine_room.flywheel.api.task.Plan
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visual.DynamicVisual.Context
import dev.engine_room.flywheel.api.visual.Visual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.task.IfElsePlan
import dev.engine_room.flywheel.lib.task.MapContextPlan
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.core.SectionPos
import net.minecraft.core.Vec3i
import org.valkyrienskies.mod.compat.flywheel.ShipSectionFlywheelModels.BuildingContext

class RenderingShipVisual(val effect: ShipEffect, val ctx: VisualizationContext) : Visual, DynamicVisual {
    private val instances = Long2ObjectOpenHashMap<TransformedInstance>()
    private val shipCenter = Vec3i(
        effect.ship.chunkClaim.xMiddle,
        effect.level.getSectionYFromSectionIndex(effect.level.sectionsCount - 1) / 2 - 1,
        effect.ship.chunkClaim.zMiddle
    )
    private val models = ShipSectionFlywheelModels(::newModel)

    private fun newModel(pos: SectionPos, model: Model?) {
        if (model == null) {
            instances.remove(pos.asLong())?.delete()
            return
        }

        val oldInstance = instances.get(pos.asLong())
        val instancer = ctx.instancerProvider().instancer(InstanceTypes.TRANSFORMED, model)

        if (oldInstance != null) {
            instancer.stealInstance(oldInstance)
        } else {
            instances.put(pos.asLong(), makeChunkInstance(pos, instancer))
        }
    }

    private fun makeChunkInstance(pos: SectionPos, instancer: Instancer<out TransformedInstance>): TransformedInstance =
        instancer.createInstance()
            .translate(
                (pos.x - shipCenter.x).toFloat() * 16,
                (pos.y - shipCenter.y).toFloat() * 16,
                (pos.z - shipCenter.z).toFloat() * 16 + 1,
            )

    override fun update(partialTick: Float) {

    }

    override fun delete() {
        instances.forEach { t, u -> u.delete() }
        instances.clear()
    }

    override fun planFrame(): Plan<Context> =
        IfElsePlan.on<Context>(effect::areSectionsDirty)
            .ifTrue(MapContextPlan.map { c: Context -> makeBuildingContext() }.to(models.createBuildingPlan()))
            .plan()

    private fun makeBuildingContext() =
        BuildingContext(
            effect.level,
            effect.pullQueuedSections()
        )
}
