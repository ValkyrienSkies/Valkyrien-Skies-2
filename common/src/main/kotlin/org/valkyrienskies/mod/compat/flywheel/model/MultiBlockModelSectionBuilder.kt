package org.valkyrienskies.mod.compat.flywheel.model

import dev.engine_room.flywheel.api.model.Model
import dev.engine_room.flywheel.api.task.Plan
import dev.engine_room.flywheel.lib.model.baked.MultiBlockModelBuilder
import dev.engine_room.flywheel.lib.task.ForEachPlan
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.BlockPos.MutableBlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.core.Vec3i
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.ColorResolver
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.lighting.LevelLightEngine
import net.minecraft.world.level.material.FluidState
import org.valkyrienskies.mod.compat.flywheel.model.FlywheelSectionModelBuilder.BuildingContext
import java.util.NoSuchElementException

class MultiBlockModelSectionBuilder : FlywheelSectionModelBuilder {
    override fun createBuildingPlan(newModel: (SectionPos, Model?) -> Unit): Plan<BuildingContext> =
        ForEachPlan.of(
            {x: BuildingContext -> x.updates.map(SectionPos::of)},
            buildChunk(newModel)
        )

    fun buildChunk(newModel: (SectionPos, Model?) -> Unit) = { pos: SectionPos, ctx: BuildingContext ->
        newModel(pos, run {
            val chunk = ctx.level.getChunk(pos.x, pos.z);
            val section = chunk.getSection(ctx.level.getSectionIndexFromSectionY(pos.y))

            if (section.hasOnlyAir()) return@run null

            MultiBlockModelBuilder.create(wrapLevel(ctx.level, pos.origin()), AllSectionPositions).apply {
                enableFluidRendering()
            }.build()
        })
    }

    private fun wrapLevel(lvl: ClientLevel, origin: Vec3i) = object : BlockAndTintGetter {
        override fun getHeight(): Int = lvl.height
        override fun getMinBuildHeight(): Int = lvl.minBuildHeight
        override fun getBlockEntity(blockPos: BlockPos): BlockEntity? =
            lvl.getBlockEntity(blockPos.offset(origin))

        override fun getBlockState(blockPos: BlockPos): BlockState =
            lvl.getBlockState(blockPos.offset(origin))

        override fun getFluidState(blockPos: BlockPos): FluidState =
            lvl.getFluidState(blockPos.offset(origin))

        override fun getShade(direction: Direction, bl: Boolean): Float = 1f

        override fun getLightEngine(): LevelLightEngine =
            lvl.lightEngine

        override fun getBlockTint(blockPos: BlockPos, colorResolver: ColorResolver): Int =
            lvl.getBlockTint(blockPos.offset(origin), colorResolver)
    }

    private object AllSectionPositions : Iterable<BlockPos> {
        override fun iterator(): Iterator<BlockPos> = MyIterator()

        private class MyIterator : Iterator<BlockPos> {
            private val pos = MutableBlockPos()
            init {
                pos.x = -1
                pos.y = 0
                pos.z = 0
            }

            override fun hasNext(): Boolean =
                pos.x < 15 || pos.y < 15 || pos.z < 15

            override fun next(): BlockPos {
                pos.x++

                if (pos.x > 15) {
                    pos.x = 0
                    pos.y++

                    if (pos.y > 15) {
                        pos.y = 0
                        pos.z++

                        if (pos.z > 15) {
                            throw NoSuchElementException()
                        }
                    }
                }

                return pos
            }
        }
    }
}
