package org.valkyrienskies.mod.feature.ship_water_pockets

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap
import java.io.DataOutput
import java.util.BitSet
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.BucketPickup
import net.minecraft.world.level.block.LiquidBlockContainer
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.Level
import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.bodies.properties.BodyKinematics
import org.valkyrienskies.core.api.bodies.properties.BodyTransform
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.air_pockets.MIN_OPENING_CONDUCTANCE
import org.valkyrienskies.mod.common.air_pockets.ShipPocketState
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager
import org.valkyrienskies.mod.common.air_pockets.WaterSolveSnapshot
import org.valkyrienskies.mod.common.util.DimensionIdProvider
import org.valkyrienskies.mod.mixinducks.feature.air_pockets.ship_water_pockets.LevelChunkDuck
import org.valkyrienskies.mod.util.FluidStateManager
import java.util.concurrent.ConcurrentHashMap

internal object ShipWaterPocketTestSupport {
    private var bootstrapped = false

    fun bootstrapMinecraft() {
        if (bootstrapped) return
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        bootstrapped = true
    }
}

internal open class TestShipTransform(
    override val position: Vector3dc = Vector3d(),
    override val rotation: Quaterniond = Quaterniond(),
    override val scaling: Vector3dc = Vector3d(1.0, 1.0, 1.0),
    override val positionInModel: Vector3dc = Vector3d(),
) : ShipTransform {
    override val toWorld = Matrix4d()
        .translate(position.x(), position.y(), position.z())
        .rotate(rotation)
        .scale(scaling.x(), scaling.y(), scaling.z())
        .translate(-positionInModel.x(), -positionInModel.y(), -positionInModel.z())
    override val toModel = Matrix4d(toWorld).invert()

    override fun withVelocity(velocity: Vector3dc, angularVelocity: Vector3dc): BodyKinematics {
        throw UnsupportedOperationException("Velocity is not used by ship water pocket tests")
    }

    override fun toBuilder(): BodyTransform.Builder {
        throw UnsupportedOperationException("Builder is not used by ship water pocket tests")
    }

    override fun writeTransform(output: DataOutput) {
        throw UnsupportedOperationException("Serialization is not used by ship water pocket tests")
    }
}

internal class IdentityShipTransform : TestShipTransform()

internal data class TestFloodContainerFixture(
    val emptyState: BlockState,
    val filledState: BlockState,
)

internal fun newTestFloodContainerFixture(): TestFloodContainerFixture {
    val block = mockk<net.minecraft.world.level.block.Block>(
        relaxed = true,
        moreInterfaces = arrayOf(LiquidBlockContainer::class, BucketPickup::class),
    )
    val emptyState = mockk<BlockState>(relaxed = true)
    val filledState = mockk<BlockState>(relaxed = true)
    val liquidBlockContainer = block as LiquidBlockContainer
    val bucketPickup = block as BucketPickup

    every { emptyState.block } returns block
    every { filledState.block } returns block
    every { emptyState.fluidState } returns Fluids.EMPTY.defaultFluidState()
    every { filledState.fluidState } returns Fluids.WATER.defaultFluidState()
    every { emptyState.isAir } returns false
    every { filledState.isAir } returns false
    every { emptyState.hasProperty(any<Property<*>>()) } returns false
    every { filledState.hasProperty(any<Property<*>>()) } returns false

    every { liquidBlockContainer.canPlaceLiquid(any(), any(), any(), any()) } answers {
        thirdArg<BlockState>() === emptyState && args[3] as Fluid == Fluids.WATER
    }
    every { liquidBlockContainer.placeLiquid(any(), any(), any(), any()) } answers {
        val state = thirdArg<BlockState>()
        val fluidState = args[3] as FluidState
        if (state !== emptyState || fluidState.type != Fluids.WATER) {
            false
        } else {
            firstArg<ServerLevel>().setBlock(secondArg(), filledState, 3)
        }
    }
    every { bucketPickup.pickupBlock(any(), any(), any()) } answers {
        val state = thirdArg<BlockState>()
        if (state !== filledState) {
            ItemStack.EMPTY
        } else {
            firstArg<ServerLevel>().setBlock(secondArg(), emptyState, 3)
            ItemStack(Items.WATER_BUCKET)
        }
    }
    every { bucketPickup.getPickupSound() } returns Fluids.WATER.pickupSound

    return TestFloodContainerFixture(
        emptyState = emptyState,
        filledState = filledState,
    )
}

internal fun bitSetOf(vararg indices: Int): BitSet {
    return BitSet().apply {
        for (idx in indices) {
            set(idx)
        }
    }
}

internal fun fullBitSet(size: Int): BitSet {
    return BitSet(size).apply { set(0, size) }
}

internal fun verticalPocketState(
    sizeY: Int,
    simulationIndices: IntArray,
    exteriorIndices: IntArray,
): ShipPocketState {
    val volume = sizeY
    val faceCondYP = ShortArray(volume)
    for (y in 0 until sizeY - 1) {
        faceCondYP[y] = MIN_OPENING_CONDUCTANCE.toShort()
    }

    return ShipPocketState(
        sizeX = 1,
        sizeY = sizeY,
        sizeZ = 1,
        open = fullBitSet(volume),
        simulationDomain = bitSetOf(*simulationIndices),
        interior = bitSetOf(*simulationIndices),
        outsideVoid = bitSetOf(*exteriorIndices),
        faceCondXP = ShortArray(volume),
        faceCondYP = faceCondYP,
        faceCondZP = ShortArray(volume),
    )
}

internal fun horizontalPocketStateX(
    sizeX: Int,
    simulationIndices: IntArray,
    exteriorIndices: IntArray,
): ShipPocketState {
    val volume = sizeX
    val faceCondXP = ShortArray(volume)
    for (x in 0 until sizeX - 1) {
        faceCondXP[x] = MIN_OPENING_CONDUCTANCE.toShort()
    }

    return ShipPocketState(
        sizeX = sizeX,
        sizeY = 1,
        sizeZ = 1,
        open = fullBitSet(volume),
        simulationDomain = bitSetOf(*simulationIndices),
        interior = bitSetOf(*simulationIndices),
        outsideVoid = bitSetOf(*exteriorIndices),
        faceCondXP = faceCondXP,
        faceCondYP = ShortArray(volume),
        faceCondZP = ShortArray(volume),
    )
}

internal fun createTrackingLevel(
    states: MutableMap<Long, BlockState>,
    gameTime: Long = 0L,
    shipObjectWorld: VsiServerShipWorld? = null,
): ServerLevel {
    val level = mockk<ServerLevel>(
        relaxed = true,
        moreInterfaces = arrayOf(DimensionIdProvider::class),
    )
    val server = mockk<MinecraftServer>(
        relaxed = true,
        moreInterfaces = arrayOf(IShipObjectWorldServerProvider::class),
    )
    val chunk = mockk<ChunkAccess>(
        relaxed = true,
        moreInterfaces = arrayOf(LevelChunkDuck::class),
    )
    val random = RandomSource.create(0L)
    val fluidData = FluidStateManager.ChunkFluidData()

    for ((key, state) in states) {
        fluidData.setFluidState(BlockPos.of(key), state.fluidState)
    }

    every { level.gameTime } returns gameTime
    every { level.minBuildHeight } returns -64
    every { level.maxBuildHeight } returns 320
    every { level.server } returns server
    every { (level as DimensionIdProvider).dimensionId } returns "minecraft:overworld"
    every { (server as IShipObjectWorldServerProvider).shipObjectWorld } returns shipObjectWorld
    every { (server as IShipObjectWorldServerProvider).vsPipeline } returns null

    every { chunk.getBlockState(any()) } answers {
        states.getOrElse(blockKey(firstArg<BlockPos>())) { Blocks.AIR.defaultBlockState() }
    }
    every { chunk.getFluidState(any()) } answers {
        states.getOrElse(blockKey(firstArg<BlockPos>())) { Blocks.AIR.defaultBlockState() }.fluidState
    }
    every { (chunk as LevelChunkDuck).`vs$getFluidData`() } returns fluidData

    every { level.getChunk(any<Int>(), any<Int>(), any(), any()) } returns chunk
    every { level.getBlockState(any<BlockPos>()) } answers {
        states.getOrElse(blockKey(firstArg<BlockPos>())) { Blocks.AIR.defaultBlockState() }
    }
    every { level.setBlock(any<BlockPos>(), any(), any()) } answers {
        val pos = firstArg<BlockPos>()
        val blockState = secondArg<BlockState>()
        states[blockKey(pos)] = blockState
        fluidData.setFluidState(pos, blockState.fluidState)
        true
    }
    every { level.destroyBlock(any<BlockPos>(), any()) } answers {
        val pos = firstArg<BlockPos>()
        states[blockKey(pos)] = Blocks.AIR.defaultBlockState()
        fluidData.setFluidState(pos, Fluids.EMPTY.defaultFluidState())
        true
    }
    every { level.scheduleTick(any<BlockPos>(), any<Fluid>(), any()) } just runs
    Level::class.java.getDeclaredField("random").apply {
        isAccessible = true
        set(level, random)
    }
    return level
}

internal fun blockKey(x: Int, y: Int, z: Int): Long = BlockPos.asLong(x, y, z)

internal fun blockKey(pos: BlockPos): Long = BlockPos.asLong(pos.x, pos.y, pos.z)

internal fun invokeDrainFloodedInteriorToOutsideAir(
    level: ServerLevel,
    state: ShipPocketState,
    shipTransform: ShipTransform,
    protectedInterior: BitSet?,
    newPlanesOut: Int2DoubleOpenHashMap,
    toRemoveAll: BitSet,
    drainSuppressedOut: BitSet = BitSet(),
) {
    val method = ShipWaterPocketManager::class.java.getDeclaredMethod(
        "drainFloodedInteriorToOutsideAir",
        ServerLevel::class.java,
        ShipPocketState::class.java,
        ShipTransform::class.java,
        BitSet::class.java,
        Int2DoubleOpenHashMap::class.java,
        BitSet::class.java,
        BitSet::class.java,
    )
    method.isAccessible = true
    method.invoke(ShipWaterPocketManager, level, state, shipTransform, protectedInterior, newPlanesOut, toRemoveAll, drainSuppressedOut)
}

internal fun invokeSyncMaterializedFloodFluidFromWorld(
    level: ServerLevel,
    state: ShipPocketState,
) {
    val method = ShipWaterPocketManager::class.java.getDeclaredMethod(
        "syncMaterializedFloodFluidFromWorld",
        ServerLevel::class.java,
        ShipPocketState::class.java,
    )
    method.isAccessible = true
    method.invoke(ShipWaterPocketManager, level, state)
}

internal fun invokeEstimateExteriorFluidSurfaceYAtShipPoint(
    level: Level,
    shipTransform: ShipTransform,
    shipX: Double,
    shipY: Double,
    shipZ: Double,
    sampleFluid: Fluid,
): Double? {
    val method = ShipWaterPocketManager::class.java.declaredMethods.single {
        it.name == "estimateExteriorFluidSurfaceYAtShipPoint"
    }
    method.isAccessible = true
    return method.invoke(
        ShipWaterPocketManager,
        level,
        shipTransform,
        shipX,
        shipY,
        shipZ,
        sampleFluid,
        Vector3d(),
        Vector3d(),
        BlockPos.MutableBlockPos(),
        null,
        null,
        null,
    ) as Double?
}

internal fun invokeCaptureWaterSolveSnapshot(
    level: Level,
    state: ShipPocketState,
    shipTransform: ShipTransform,
    generation: Long = 1L,
    captureTick: Long = 0L,
): WaterSolveSnapshot? {
    val method = ShipWaterPocketManager::class.java.declaredMethods.single {
        it.name == "captureWaterSolveSnapshot"
    }
    method.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return method.invoke(
        ShipWaterPocketManager,
        level,
        state,
        shipTransform,
        generation,
        captureTick,
        null,
        null,
    ) as WaterSolveSnapshot?
}

internal fun invokeComputeWaterReachable(
    level: Level,
    state: ShipPocketState,
    shipTransform: ShipTransform,
): BitSet {
    val method = ShipWaterPocketManager::class.java.getDeclaredMethod(
        "computeWaterReachable",
        Level::class.java,
        ShipPocketState::class.java,
        ShipTransform::class.java,
    )
    method.isAccessible = true
    return method.invoke(ShipWaterPocketManager, level, state, shipTransform) as BitSet
}

internal fun <T> withRegisteredServerState(
    level: ServerLevel,
    shipId: Long,
    state: ShipPocketState,
    block: () -> T,
): T {
    @Suppress("UNCHECKED_CAST")
    val serverStates =
        ShipWaterPocketManager::class.java.getDeclaredField("serverStates").apply { isAccessible = true }
            .get(ShipWaterPocketManager) as ConcurrentHashMap<String, ConcurrentHashMap<Long, ShipPocketState>>

    val dimensionId = (level as DimensionIdProvider).dimensionId
    val statesForDimension = serverStates.computeIfAbsent(dimensionId) { ConcurrentHashMap() }
    val previous = statesForDimension.put(shipId, state)
    return try {
        block()
    } finally {
        if (previous != null) {
            statesForDimension[shipId] = previous
        } else {
            statesForDimension.remove(shipId)
            if (statesForDimension.isEmpty()) {
                serverStates.remove(dimensionId, statesForDimension)
            }
        }
    }
}
