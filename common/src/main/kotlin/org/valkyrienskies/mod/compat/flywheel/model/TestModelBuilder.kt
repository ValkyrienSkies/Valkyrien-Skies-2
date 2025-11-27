package org.valkyrienskies.mod.compat.flywheel.model

import dev.engine_room.flywheel.api.model.Model
import dev.engine_room.flywheel.api.task.Plan
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.task.ForEachPlan
import net.minecraft.core.SectionPos
import net.minecraft.world.level.block.Blocks
import org.valkyrienskies.mod.compat.flywheel.model.FlywheelSectionModelBuilder.BuildingContext

class TestModelBuilder : FlywheelSectionModelBuilder {
    override fun createBuildingPlan(newModel: (SectionPos, Model?) -> Unit): Plan<BuildingContext> =
        ForEachPlan.of(
            {x: BuildingContext -> x.updates.map(SectionPos::of)},
            buildChunk(newModel)
        )

    fun buildChunk(newModel: (SectionPos, Model?) -> Unit) = { pos: SectionPos, ctx: BuildingContext ->
        newModel(pos, run {
            Models.block(Blocks.DIRT.defaultBlockState())
        })
    }
}
