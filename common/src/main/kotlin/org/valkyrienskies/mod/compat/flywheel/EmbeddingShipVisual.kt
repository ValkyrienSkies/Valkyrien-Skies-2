package org.valkyrienskies.mod.compat.flywheel

import dev.engine_room.flywheel.api.task.Plan
import dev.engine_room.flywheel.api.visual.DistanceUpdateLimiter
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visual.DynamicVisual.Context
import dev.engine_room.flywheel.api.visual.EffectVisual
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual
import dev.engine_room.flywheel.api.visual.SectionTrackedVisual.SectionCollector
import dev.engine_room.flywheel.api.visual.ShaderLightVisual
import dev.engine_room.flywheel.api.visual.TickableVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.impl.task.TaskExecutorImpl
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl
import dev.engine_room.flywheel.lib.task.ForEachPlan
import dev.engine_room.flywheel.lib.task.IfElsePlan
import dev.engine_room.flywheel.lib.task.MapContextPlan
import dev.engine_room.flywheel.lib.task.NestedPlan
import dev.engine_room.flywheel.lib.task.RunnablePlan
import it.unimi.dsi.fastutil.longs.LongArraySet
import it.unimi.dsi.fastutil.longs.LongSet
import net.minecraft.client.Camera
import net.minecraft.core.SectionPos
import net.minecraft.util.Mth
import org.joml.FrustumIntersection
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.valkyrienskies.core.api.world.LevelYRange
import org.valkyrienskies.mod.common.config.ShipRenderer.FLYWHEEL
import org.valkyrienskies.mod.common.config.shipRenderer
import org.valkyrienskies.mod.common.util.toBlockPos
import org.valkyrienskies.mod.common.util.toFloat
import org.valkyrienskies.mod.common.util.toJOMLD

class EmbeddingShipVisual(val effect: ShipEffect, val visualContext: VisualizationContext) :
    EffectVisual<ShipEffect>, DynamicVisual, TickableVisual, ShaderLightVisual, LightUpdatedVisual
{
    val ship get() = effect.ship
    val embedding = visualContext.createEmbedding(
        effect.ship.chunkClaim.getCenterBlockCoordinates(
            LevelYRange(effect.level.minBuildHeight, effect.level.maxBuildHeight - 1)
        ).toBlockPos()
    )
    //TODO uses impl prone to change will prob break
    val storage = ShipBlockEntityStorage(embedding)
    val manager = VisualManagerImpl(storage).apply { effect.manager = this }
    val camera = ShipEffectCamera(ship)
    val frustum = FrustumIntersection()

    lateinit var collector: SectionCollector
    var minSection: Long = 0
    var maxSection: Long = 0

    var renderingShipVisual: RenderingShipVisual? = null

    override fun update(partialTick: Float) {
        if (renderingShipVisual == null && ship.shipRenderer == FLYWHEEL) {
            renderingShipVisual = RenderingShipVisual(effect, embedding)
        } else if (renderingShipVisual != null) {
            if (ship.shipRenderer != FLYWHEEL) {
                renderingShipVisual!!.delete()
                renderingShipVisual = null;
            } else {
                renderingShipVisual!!.update(partialTick)
            }
        }
    }

    override fun updateLight(partialTick: Float) {

    }

    override fun planTick(): Plan<TickableVisual.Context> =
        manager.tickPlan()

    override fun planFrame(): Plan<Context> =
        NestedPlan.of(
            RunnablePlan.of(::updateEmbedding),
            RunnablePlan.of(::updateSections),
            MapContextPlan.map(::newContext).to(manager.framePlan()),
            IfNotNullPlan({renderingShipVisual}, RenderingShipVisual::planFrame)
        )

    private fun newContext(ctx: Context): Context {
        camera.update(ctx.camera())
        val pos = ship.renderTransform.shipToWorld.transformPosition(embedding.renderOrigin().toJOMLD())
        val rotation = ship.renderTransform.shipToWorldRotation.toFloat()
        val scale = ship.renderTransform.shipToWorldScaling

        val viewProjection = Matrix4f(FlywheelCompat.viewProjection)
            .translate(
                (pos.x - ctx.camera().position.x).toFloat(),
                (pos.y - ctx.camera().position.y).toFloat(),
                (pos.z - ctx.camera().position.z).toFloat()
            )
            .scale(
                scale.x().toFloat(),
                scale.y().toFloat(),
                scale.z().toFloat()
            )
            .rotate(rotation)

        frustum.set(viewProjection)

        return object : Context {
            override fun camera(): Camera = camera
            override fun frustum(): FrustumIntersection = frustum
            override fun partialTick(): Float = ctx.partialTick()
            override fun limiter(): DistanceUpdateLimiter = ctx.limiter()
        }
    }


    private fun updateEmbedding(ctx: Context) {
        val pos = ship.renderTransform.shipToWorld.transformPosition(embedding.renderOrigin().toJOMLD())
        val rotation = ship.renderTransform.shipToWorldRotation.toFloat()
        val scale = ship.renderTransform.shipToWorldScaling

        val pose = Matrix4f().identity()
            .translate(
                (pos.x() - visualContext.renderOrigin().x).toFloat(),
                (pos.y() - visualContext.renderOrigin().y).toFloat(),
                (pos.z() - visualContext.renderOrigin().z).toFloat()
            )
            .scale(
                scale.x().toFloat(),
                scale.y().toFloat(),
                scale.z().toFloat()
            )
            .rotate(rotation)

        val normal = Matrix3f().set(rotation)

        embedding.transforms(pose, normal)
    }

    private fun updateSections(ctx: Context) {
        if (hasMovedSections())
            collector.sections(collectLightSections())
    }

    private fun collectLightSections(): LongSet {
        val boundingBox = ship.renderAABB
        val minSectionX = minLightSection(boundingBox.minX())
        val minSectionY = minLightSection(boundingBox.minY())
        val minSectionZ = minLightSection(boundingBox.minZ())
        val maxSectionX = maxLightSection(boundingBox.maxX())
        val maxSectionY = maxLightSection(boundingBox.maxY())
        val maxSectionZ = maxLightSection(boundingBox.maxZ())

        minSection = SectionPos.asLong(minSectionX, minSectionY, minSectionZ)
        maxSection = SectionPos.asLong(maxSectionX, maxSectionY, maxSectionZ)

        val longSet: LongSet = LongArraySet()

        for (x in 0..maxSectionX - minSectionX) {
            for (y in 0..maxSectionY - minSectionY) {
                for (z in 0..maxSectionZ - minSectionZ) {
                    longSet.add(SectionPos.offset(minSection, x, y, z))
                }
            }
        }

        return longSet
    }

    private fun hasMovedSections(): Boolean {
        val boundingBox = ship.renderAABB
        val minSectionX = minLightSection(boundingBox.minX())
        val minSectionY = minLightSection(boundingBox.minY())
        val minSectionZ = minLightSection(boundingBox.minZ())
        val maxSectionX = maxLightSection(boundingBox.maxX())
        val maxSectionY = maxLightSection(boundingBox.maxY())
        val maxSectionZ = maxLightSection(boundingBox.maxZ())

        return minSection != SectionPos.asLong(minSectionX, minSectionY, minSectionZ)
            || maxSection != SectionPos.asLong(maxSectionX, maxSectionY, maxSectionZ)
    }

    override fun delete() {
        manager.invalidate()
        storage.invalidate()
        renderingShipVisual?.delete()
        embedding.delete()
    }

    override fun setSectionCollector(collector: SectionCollector) {
        this.collector = collector
    }

    companion object {
        const val LIGHT_PADDING: Int = 1

        fun minLight(aabbPos: Double): Int {
            return Mth.floor(aabbPos) - LIGHT_PADDING
        }

        fun maxLight(aabbPos: Double): Int {
            return Mth.ceil(aabbPos) + LIGHT_PADDING
        }

        fun minLightSection(aabbPos: Double): Int {
            return SectionPos.blockToSectionCoord(minLight(aabbPos))
        }

        fun maxLightSection(aabbPos: Double): Int {
            return SectionPos.blockToSectionCoord(maxLight(aabbPos))
        }
    }
}
