package org.valkyrienskies.compat.create

import com.jozufozu.flywheel.backend.Backend
import com.jozufozu.flywheel.backend.instancing.IInstance
import com.jozufozu.flywheel.backend.instancing.InstancedRenderRegistry
import com.jozufozu.flywheel.backend.model.ModelRenderer
import com.jozufozu.flywheel.event.BeginFrameEvent
import com.jozufozu.flywheel.event.RenderLayerEvent
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Matrix4f
import com.simibubi.create.content.contraptions.components.structureMovement.render.ActorInstance
import com.simibubi.create.content.contraptions.components.structureMovement.render.ContraptionProgram
import com.simibubi.create.foundation.render.CreateContexts
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.AABB
import org.joml.Matrix4d
import org.valkyrienskies.core.api.ClientShip
import org.valkyrienskies.mod.common.util.toMinecraft

// Based of https://github.com/Creators-of-Create/Create/blob/mc1.16/dev/src/main/java/com/simibubi/create/content/contraptions/components/structureMovement/render/FlwContraption.java
class FlwShip(val ship: ClientShip, private val level: Level) {

    val materialManager = VSMaterialManager(CreateContexts.CWORLD, this)
    { manager, state -> VSMaterialGroup(this, manager, state) }

    private val kinetics = VSInstanceManager(this)
    private val actors = mutableListOf<ActorInstance>()

    private val renderLayers: MutableMap<RenderType, ModelRenderer> = HashMap()
    private val modelMatrix = Matrix4f()

    private var isVisible = true // TODO

    fun beginFrame(event: BeginFrameEvent) {
        modelMatrix.setIdentity()

        if (!isVisible) return

        kinetics.beginFrame(event.info)
    }

    fun setupMatrices(viewProjection: PoseStack, camX: Double, camY: Double, camZ: Double) {

        val originInShipSpace = materialManager.originCoordinate

        viewProjection.pushPose()

        modelMatrix.set(viewProjection.last().pose())
        modelMatrix.multiply(
            ship.shipTransform.shipToWorldMatrix.translate(
                originInShipSpace.x.toDouble() - camX,
                originInShipSpace.y.toDouble() - camY,
                originInShipSpace.z.toDouble() - camZ, Matrix4d()
            ).toMinecraft()
        )

        viewProjection.popPose()
    }

    fun setup(program: ContraptionProgram) {
        program.bind(modelMatrix, AABB.ofSize(16.0, 16.0, 16.0))
    }

    fun renderInstances(event: RenderLayerEvent) {
        if (!isVisible) return

        if (Backend.getInstance().canUseInstancing()) {
            val renderLayer = event.getLayer()
            if (renderLayer != null) {
                materialManager.render(
                    renderLayer, event.viewProjection, event.camX, event.camY, event.camZ
                )
            }
        }
    }

    fun invalidate() {
        for (buffer in renderLayers.values) {
            buffer.delete()
        }

        renderLayers.clear()
        materialManager.delete()
        kinetics.invalidate()
    }

    fun addInstancedTile(be: BlockEntity) {
        if (InstancedRenderRegistry.getInstance().canInstance(be.type)) {
            return kinetics.add(be)
        }
    }

    fun removeInstancedTile(be: BlockEntity) {
        if (InstancedRenderRegistry.getInstance().canInstance(be.type)) {
            kinetics.remove(be)
        }
    }

    fun addActor() {
    }

    fun getIInstance(be: BlockEntity): IInstance? = kinetics.get(be)
}
