package org.valkyrienskies.mod.common.fluid

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import org.valkyrienskies.mod.compat.LoadedMods
import org.valkyrienskies.mod.compat.flooding.shaders.NativeFluidCompat
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.AABB
import org.joml.Vector3d
import org.valkyrienskies.mod.common.networking.PacketFluidParcel
import org.valkyrienskies.mod.common.render.batched.ShipRenderFrustum
import java.util.UUID

object FluidParcelRenderer {
    private data class Update(val packet: PacketFluidParcel, val source: ClientPacketListener, val level: ClientLevel?)
    private val pending = LinkedHashMap<UUID, Update>()
    private val visuals = FluidParcelVisuals()
    private val clock = FluidParcelClock()
    private var meshInputs = emptyList<FluidParcelCuboids.Parcel>()
    private var cachedMesh: FluidParcelCuboids.Mesh? = null
    private var scheduled = false
    private val packBuilder = BufferBuilder(64 * 1024)
    private var shaderFrame: (() -> Unit)? = null
    private var renderingShaderFrame = false
    private var opaquePass = false
    private var shaderSampleTime = 0L
    private var level: ClientLevel? = null

    fun enqueue(packet: PacketFluidParcel, source: ClientPacketListener) = enqueue(listOf(packet), source)

    internal fun enqueue(packets: List<PacketFluidParcel>, source: ClientPacketListener) {
        val minecraft = Minecraft.getInstance()
        synchronized(pending) {
            val current = minecraft.level
            for (packet in packets) {
                val old = pending[packet.id]
                if (old != null && old.source === source && old.packet.revision >= packet.revision) continue
                if (old == null && pending.size >= 4096) continue
                pending[packet.id] = Update(packet, source, current)
            }
            if (scheduled || pending.isEmpty()) return
            scheduled = true
        }
        minecraft.execute {
            val updates = synchronized(pending) {
                val values = pending.values.toList()
                pending.clear()
                scheduled = false
                values
            }
            val current = minecraft.level
            bindLevel(current)
            val now = clock.sample(System.nanoTime(), minecraft.isPaused)
            for (update in updates) {
                if (minecraft.connection === update.source && current != null && current === update.level &&
                    current.dimension().location().toString() == update.packet.dimension) visuals.accept(update.packet, now)
            }
        }
    }

    private fun bindLevel(current: ClientLevel?) {
        if (level === current) return
        visuals.clear()
        meshInputs = emptyList()
        cachedMesh = null
        clock.reset()
        level = current
    }

    @JvmStatic
    fun clear() {
        shaderFrame = null
        synchronized(pending) { pending.clear() }
        visuals.clear()
        meshInputs = emptyList()
        cachedMesh = null
        clock.reset()
        level = null
    }

    @JvmStatic
    fun renderShaderFrame() = renderShaderFrame(false)

    @JvmStatic
    fun renderOpaqueShaderFrame() = renderShaderFrame(true)

    private fun renderShaderFrame(opaque: Boolean) {
        val frame = shaderFrame ?: return
        shaderFrame = null
        renderingShaderFrame = true
        opaquePass = opaque
        try { frame() } finally {
            renderingShaderFrame = false; opaquePass = false; shaderFrame = if (opaque) frame else null
        }
    }

    @JvmStatic
    fun render(matrices: PoseStack, buffers: MultiBufferSource.BufferSource,
        cameraX: Double, cameraY: Double, cameraZ: Double) {
        val minecraft = Minecraft.getInstance()
        val current = minecraft.level
        if (current == null) { clear(); return }
        bindLevel(current)
        val nativeMaterial = LoadedMods.iris && NativeFluidCompat.active()
        if (nativeMaterial && !renderingShaderFrame) {
            val pose = PoseStack().apply { mulPoseMatrix(org.joml.Matrix4f(matrices.last().pose())) }
            shaderSampleTime = clock.sample(System.nanoTime(), minecraft.isPaused)
            shaderFrame = { render(pose, buffers, cameraX, cameraY, cameraZ) }
            return
        }
        val samples = visuals.samples(if (nativeMaterial) shaderSampleTime else clock.sample(System.nanoTime(), minecraft.isPaused))

        if (samples.isEmpty()) {
            meshInputs = emptyList()
            cachedMesh = null
            return
        }
        val materials = samples.map { it.state.fluid }.distinct()
        val materialIds = materials.withIndex().associate { it.value to it.index }
        val fluids = materials.map { name -> ResourceLocation.tryParse(name)?.let { BuiltInRegistries.FLUID.get(it).defaultFluidState() } }
        if (nativeMaterial && fluids.none { it != null && it.`is`(net.minecraft.tags.FluidTags.LAVA) == opaquePass }) return
        val inputs = samples.map {
            FluidParcelCuboids.Parcel(it.position, it.state.volume, materialIds.getValue(it.state.fluid))
        }
        val mesh = cachedMesh?.takeIf { inputs == meshInputs } ?: FluidParcelCuboids.build(inputs).also {
            meshInputs = inputs
            cachedMesh = it
        }
        val origin = mesh.origin()
        val localCamera = Vector3d(cameraX, cameraY, cameraZ).sub(origin)
        mesh.faces().sortByDescending { it.distanceSquared(localCamera.x, localCamera.y, localCamera.z) }
        val frustum = ShipRenderFrustum.create(matrices.last().pose(), RenderSystem.getProjectionMatrix(), cameraX, cameraY, cameraZ)
        val type = if (nativeMaterial) (if (opaquePass) RenderType.solid() else RenderType.translucent()) else RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)
        if (nativeMaterial) packBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK)
        val consumer = if (nativeMaterial) packBuilder else buffers.getBuffer(type)
        val appearances = HashMap<Pair<Int, BlockPos>, Pair<FluidAppearance, Int>?>()
        val sodium = org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin.getVSRenderer() ==
            org.valkyrienskies.mod.compat.VSRenderer.SODIUM
        try {
            for (face in mesh.faces()) {
                val x0 = origin.x() + face.coordinate(0, face.minU(), face.minV())
                val y0 = origin.y() + face.coordinate(1, face.minU(), face.minV())
                val z0 = origin.z() + face.coordinate(2, face.minU(), face.minV())
                val x1 = origin.x() + face.coordinate(0, face.maxU(), face.maxV())
                val y1 = origin.y() + face.coordinate(1, face.maxU(), face.maxV())
                val z1 = origin.z() + face.coordinate(2, face.maxU(), face.maxV())
                if (!frustum.isVisible(AABB(x0, y0, z0, x1, y1, z1).inflate(1e-6))) continue
                val material = face.material()
                val fluid = fluids[material] ?: continue
                if (fluid.isEmpty || nativeMaterial && fluid.`is`(net.minecraft.tags.FluidTags.LAVA) != opaquePass) continue
                val pos = BlockPos.containing((x0 + x1) * 0.5, (y0 + y1) * 0.5, (z0 + z1) * 0.5)
                val key = material to pos
                val appearanceAndLight = if (appearances.containsKey(key)) appearances[key] else {
                    FluidAppearance.resolve(current, pos, fluid)?.let { it to it.light(LevelRenderer.getLightColor(current, pos)) }
                        .also { appearances[key] = it }
                }
                val (appearance, light) = appearanceAndLight ?: continue
                if (nativeMaterial) NativeFluidCompat.beginFluid(packBuilder, fluid, pos.x and 15, pos.y and 15, pos.z and 15)
                val sprite = appearance.still()
                if (sodium) org.valkyrienskies.mod.compat.sodium.SodiumFluidSprites.markActive(listOf(sprite))
                val size = maxOf(1.0, kotlin.math.ceil(maxOf(face.maxU() - face.minU(), face.maxV() - face.minV()) / 8))
                val startU = kotlin.math.floor((face.minU() + origin[face.u()]) / size) * size - origin[face.u()]
                val startV = kotlin.math.floor((face.minV() + origin[face.v()]) / size) * size - origin[face.v()]
                for (i in 0..9) {
                    val tileU = startU + i * size
                    if (tileU >= face.maxU()) break
                    val minU = maxOf(tileU, face.minU())
                    val maxU = minOf(tileU + size, face.maxU())
                    for (j in 0..9) {
                        val tileV = startV + j * size
                        if (tileV >= face.maxV()) break
                        val minV = maxOf(tileV, face.minV())
                        val maxV = minOf(tileV + size, face.maxV())
                        for (corner in 0..3) {
                            val index = if (face.sign() > 0) corner else 3 - corner
                            val u = if (index == 0 || index == 3) minU else maxU
                            val v = if (index < 2) minV else maxV
                            consumer.vertex(matrices.last().pose(), (face.coordinate(0, u, v) - localCamera.x).toFloat(),
                                (face.coordinate(1, u, v) - localCamera.y).toFloat(),
                                (face.coordinate(2, u, v) - localCamera.z).toFloat())
                                .color(appearance.red(), appearance.green(), appearance.blue(), appearance.alpha())
                                .uv(sprite.getU(((u - tileU) / size).coerceIn(0.0, 1.0) * 16),
                                    sprite.getV(((v - tileV) / size).coerceIn(0.0, 1.0) * 16))
                                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                                .normal(matrices.last().normal(), if (face.axis() == 0) face.sign().toFloat() else 0f,
                                    if (face.axis() == 1) face.sign().toFloat() else 0f,
                                    if (face.axis() == 2) face.sign().toFloat() else 0f).endVertex()
                        }
                    }
                }
            }
            if (nativeMaterial) {
                val rendered = packBuilder.endOrDiscardIfEmpty()
                if (rendered != null) {
                    type.setupRenderState()
                    RenderSystem.disableCull()
                    try {
                        NativeFluidCompat.drawing = true
                        RenderSystem.setShader { NativeFluidCompat.shader(opaquePass)!! }
                        BufferUploader.drawWithShader(rendered)
                    } finally {
                        NativeFluidCompat.drawing = false
                        RenderSystem.enableCull()
                        type.clearRenderState()
                    }
                }
            } else buffers.endBatch(type)
        } finally {
            if (nativeMaterial) {
                NativeFluidCompat.endFluid(packBuilder)
                if (packBuilder.building()) packBuilder.discard()
            }
        }
    }
}
