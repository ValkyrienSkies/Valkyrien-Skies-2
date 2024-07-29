package org.valkyrienskies.mod.compat.flywheel.model

import dev.engine_room.flywheel.api.model.Model
import dev.engine_room.flywheel.api.task.Plan

import it.unimi.dsi.fastutil.longs.LongSet
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.SectionPos

interface FlywheelSectionModelBuilder {
    fun createBuildingPlan(newModel: (SectionPos, Model?) -> Unit): Plan<BuildingContext>

    data class BuildingContext(
        val level: ClientLevel,
        val updates: LongSet
    )
}
