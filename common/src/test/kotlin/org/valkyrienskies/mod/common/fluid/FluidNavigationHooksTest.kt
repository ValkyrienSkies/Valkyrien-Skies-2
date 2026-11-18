package org.valkyrienskies.mod.common.fluid

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.*
import org.joml.Matrix4d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.mod.common.*
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.pathfinding.*
import org.valkyrienskies.mod.common.util.ShipPathfindingUtils
import org.valkyrienskies.mod.mixin.feature.ai.node_evaluator.*
import org.valkyrienskies.mod.mixin.feature.ai.swim.MixinRandomStrollSwim
import org.valkyrienskies.mod.mixin.feature.ai.swim.MixinBehaviorUtilsSwim
import org.valkyrienskies.mod.mixin.feature.ai.goal.MixinGoalUtil
import org.valkyrienskies.mod.mixin.feature.ai.frog.MixinFrogspawnFluidState

class FluidNavigationHooksTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }
    private val level = mockk<Level>()
    private val world = mockk<VsiClientShipWorld>()
    private val ship = mockk<ClientShip>()
    private val local = BlockPos(1000, 0, 0)
    private var fill = 255
    private var fluidId = 17
    private var sequence = 4L
    private var ambient: BlockState = Blocks.WATER.defaultBlockState()
    private var oldAi = true
    private val nearby get() = arrayOf(NearbyShip(ship, AABBd(-2.0, -2.0, -2.0, 2.0, 2.0, 2.0),
        Matrix4d().translation(1000.0, 0.0, 0.0)))

    private fun tagged(real: FluidState): FluidState = spyk(real).also {
        every { it.`is`(FluidTags.WATER) } returns real.type.isSame(Fluids.WATER)
        every { it.`is`(FluidTags.LAVA) } returns real.type.isSame(Fluids.LAVA)
    }

    @BeforeEach fun setup() {
        oldAi = VSGameConfig.SERVER.aiOnShips
        VSGameConfig.SERVER.aiOnShips = true
        PathfindingFrame.CURRENT.set(PathfindingFrame.World)
        PathfindStateCache.reset()
        every { ship.id } returns 7
        every { ship.worldAABB } answers { nearby[0].worldAABB }
        every { ship.worldToShip } returns Matrix4d().translation(1000.0, 0.0, 0.0)
        every { ship.shipToWorld } returns Matrix4d().translation(-1000.0, 0.0, 0.0)
        every { world.loadedShips.iterator() } answers { listOf(ship).iterator() }
        every { world.allShips.getIntersecting(any(), any()) } returns listOf(ship)
        every { level.getBlockState(any()) } answers { if (firstArg<BlockPos>() == BlockPos.ZERO) ambient else Blocks.AIR.defaultBlockState() }
        every { world.getFluidGameplaySnapshot(7) } answers {
            gameplaySnapshot(topologySnapshot().copy(voxels = listOf(VsiFluidTopologyVoxel(1000, 0, 0, 1))),
                floodingSnapshot(*if (fill == 0) emptyArray() else arrayOf(VsiFluidFloodedVoxel(1000, 0, 0, fluidId, fill, 0)))
                    .copy(streamSequence = sequence))
        }
        mockkObject(MassDatapackResolver)
        for ((id, real) in listOf(17 to Fluids.WATER, 18 to Fluids.LAVA)) {
            val fluid = mockk<FlowingFluid>()
            every { MassDatapackResolver.getFlowingFluid(id, true) } returns fluid
            every { fluid.getSource(false) } answers { tagged(real.getSource(false)) }
            every { fluid.getFlowing(any(), false) } answers { tagged(real.getFlowing(firstArg(), false)) }
        }
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { level.shipWorldNullable } returns world
        every { level.dimensionId } returns "test"
        every { level.getShipManagingPos(any<BlockPos>()) } answers { if (arg<BlockPos>(1).x >= 1000) ship else null }
        every { level.getShipsIntersecting(any<org.joml.primitives.AABBdc>()) } returns listOf(ship)
    }
    @AfterEach fun cleanup() {
        PathfindingFrame.CURRENT.remove()
        PathfindStateCache.reset()
        ShipFluidInteraction.clear(world)
        VSGameConfig.SERVER.aiOnShips = oldAi
        unmockkAll()
    }
    private fun invoke(type: Class<*>, target: Any?, name: String, vararg args: Any?): Any? =
        type.declaredMethods.single { it.name == name }.apply { isAccessible = true }.invoke(target, *args)
    private fun scope() = ShipFluidStateQueries.beginPath(level, PathfindingFrame.World, nearby)
    private fun region() = object : MixinPathNavigationRegion(), PathNavigationRegionAccessor {
        override fun getLevel(): Level = this@FluidNavigationHooksTest.level
    }
    private fun swim() = object : SwimNodeEvaluatorMixin() {
        override fun getStart() = Node(0, 0, 0)
        override fun getGoal(x: Double, y: Double, z: Double): net.minecraft.world.level.pathfinder.Target = mockk()
        override fun getNeighbors(nodes: Array<Node>, node: Node) = 0
        override fun getBlockPathType(getter: BlockGetter, x: Int, y: Int, z: Int, mob: Mob) = BlockPathTypes.OPEN
        override fun getBlockPathType(getter: BlockGetter, x: Int, y: Int, z: Int) = BlockPathTypes.OPEN
    }

    @Test fun `region fluid and block peeks consistently override wet and dry vanilla state`() {
        for (amount in listOf(255, 1, 0)) {
            fill = amount; sequence++
            scope().use {
                val region = region()
                val fluid = CallbackInfoReturnable("fluid", true, Fluids.WATER.defaultFluidState())
                invoke(MixinPathNavigationRegion::class.java, region, "vs\$frameAwareGetFluidState", BlockPos.ZERO, fluid)
                val block = CallbackInfoReturnable("block", true, Blocks.WATER.defaultBlockState())
                invoke(MixinPathNavigationRegion::class.java, region, "vs\$frameAwareGetBlockState", BlockPos.ZERO, block)
                assertEquals(amount == 0, fluid.returnValue.isEmpty)
                assertEquals(amount == 0, block.returnValue.isAir)
                assertEquals(fluid.returnValue.amount, block.returnValue.fluidState.amount)
            }
        }
    }

    @Test fun `swim fluid and pathfindable hooks reject dry ocean and accept retained shallow water`() {
        val target = swim()
        for (amount in listOf(0, 1, 255)) {
            fill = amount; sequence++
            scope().use {
                val state = invoke(SwimNodeEvaluatorMixin::class.java, null, "vs\$peekFluid", level, BlockPos.ZERO,
                    Operation<FluidState> { Fluids.WATER.defaultFluidState() }) as FluidState
                assertEquals(amount == 0, state.isEmpty)
                val block = invoke(SwimNodeEvaluatorMixin::class.java, null, "vs\$peekBlock", level, BlockPos.ZERO,
                    Operation<BlockState> { Blocks.AIR.defaultBlockState() }) as BlockState
                assertEquals(amount == 0, block.isAir)
                for (vanilla in listOf(false, true)) {
                    assertEquals(amount > 0, invoke(SwimNodeEvaluatorMixin::class.java, target, "vs\$wrapIsPathFindable",
                        Blocks.WATER.defaultBlockState(), level, BlockPos.ZERO, PathComputationType.WATER,
                        Operation<Boolean> { vanilla }))
                }
            }
        }
    }

    @Test fun `vanilla walk node classification consumes the region wet dry and hazard views`() {
        val region = region()
        val getter = mockk<BlockGetter>()
        every { getter.getBlockState(any()) } answers {
            val pos = firstArg<BlockPos>()
            val result = CallbackInfoReturnable("block", true, level.getBlockState(pos))
            invoke(MixinPathNavigationRegion::class.java, region, "vs\$frameAwareGetBlockState", pos, result)
            result.returnValue
        }
        every { getter.getFluidState(any()) } answers {
            val pos = firstArg<BlockPos>()
            val result = CallbackInfoReturnable("fluid", true, level.getBlockState(pos).fluidState)
            invoke(MixinPathNavigationRegion::class.java, region, "vs\$frameAwareGetFluidState", pos, result)
            result.returnValue
        }
        for (id in listOf(17, 18)) {
            fluidId = id
            for (amount in listOf(0, 1, 255)) {
                fill = amount; sequence++
                scope().use {
                    assertEquals(if (amount == 0) BlockPathTypes.OPEN else if (id == 17) BlockPathTypes.WATER else BlockPathTypes.LAVA,
                        invoke(WalkNodeEvaluator::class.java, null, "getBlockPathTypeRaw", getter, BlockPos.ZERO))
                }
            }
        }
        every { level.getBlockState(local) } returns Blocks.SWEET_BERRY_BUSH.defaultBlockState()
        scope().use {
            assertEquals(BlockPathTypes.DAMAGE_OTHER,
                invoke(WalkNodeEvaluator::class.java, null, "getBlockPathTypeRaw", getter, BlockPos.ZERO))
        }
    }

    @Test fun `general local queries preserve newly solid and waterlogged blocks`() {
        for (state in listOf(Blocks.STONE.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState()
            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true))) {
            every { level.getBlockState(local) } returns state
            assertNull(ShipFluidStateQueries.local(level, ship, local))
        }
    }

    @Test fun `ordinary swim and water-goal hooks consult domains before accepting ambient water`() {
        val mob = mockk<PathfinderMob>()
        every { mob.level() } returns level
        for (amount in listOf(0, 128)) {
            fill = amount; sequence++
            for ((type, method) in listOf(
                MixinRandomStrollSwim::class.java to "vs\$shipAwareSwimFluid",
                MixinGoalUtil::class.java to "vs\$getFluidStateIsWater")) {
                val result = invoke(type, null, method, level, BlockPos.ZERO,
                    Operation<FluidState> { Fluids.WATER.defaultFluidState() }) as FluidState
                assertEquals(amount == 0, result.isEmpty)
            }
            assertEquals(amount > 0, invoke(MixinBehaviorUtilsSwim::class.java, null, "vs\$shipAwareSwimmable",
                Blocks.WATER.defaultBlockState(), level, BlockPos.ZERO, PathComputationType.WATER,
                Operation<Boolean> { true }, mob))
        }
    }

    @Test fun `general ship-aware helpers expose local and world retained state and authoritative dry`() {
        for (amount in listOf(0, 128)) {
            fill = amount; sequence++
            assertEquals(amount == 0, ShipPathfindingUtils.getShipAwareFluidState(level, local, null).isEmpty)
            assertEquals(amount == 0, ShipPathfindingUtils.getShipAwareFluidState(level, BlockPos.ZERO, null).isEmpty)
            assertEquals(amount == 0, ShipPathfindingUtils.findExactShipFluid(level, BlockPos.ZERO).isEmpty)
        }
    }

    @Test fun `one path stays coherent across new publications and nested scopes release their cache`() {
        scope().use {
            val original = ShipFluidStateQueries.forPath(level, BlockPos.ZERO)!!.fluid()
            fill = 0; sequence++
            assertEquals(original.amount, ShipFluidStateQueries.forPath(level, BlockPos.ZERO)!!.fluid().amount)
            scope().use { assertTrue(ShipFluidStateQueries.forPath(level, BlockPos.ZERO)!!.fluid().isEmpty) }
            assertEquals(original.amount, ShipFluidStateQueries.forPath(level, BlockPos.ZERO)!!.fluid().amount)
            verify(exactly = 3) { world.getFluidGameplaySnapshot(7) }
            verify(exactly = 1) { world.loadedShips.iterator() }
        }
        scope().use { assertTrue(ShipFluidStateQueries.forPath(level, BlockPos.ZERO)!!.fluid().isEmpty) }
    }

    @Test fun `disabled AI preserves native navigation decisions`() {
        VSGameConfig.SERVER.aiOnShips = false
        fill = 0
        val original = Fluids.WATER.defaultFluidState()
        assertSame(original, invoke(SwimNodeEvaluatorMixin::class.java, null, "vs\$peekFluid", level, BlockPos.ZERO,
            Operation<FluidState> { original }))
        val fluid = CallbackInfoReturnable("fluid", true, original)
        invoke(MixinPathNavigationRegion::class.java, region(), "vs\$frameAwareGetFluidState", BlockPos.ZERO, fluid)
        assertSame(original, fluid.returnValue)
        verify(exactly = 0) { world.getFluidGameplaySnapshot(any()) }
    }

    @Test fun `frogspawn support sees retained source water and clears when the source drains`() {
        for (amount in listOf(255, 254, 0)) {
            fill = amount; sequence++
            val state = invoke(MixinFrogspawnFluidState::class.java, null, "vs\$shipFluidSupport", level, local,
                Operation<FluidState> { Fluids.EMPTY.defaultFluidState() }) as FluidState
            assertEquals(amount == 255, state.type == Fluids.WATER)
        }
        val ordinary = mockk<BlockGetter>()
        val original = Fluids.WATER.defaultFluidState()
        assertSame(original, invoke(MixinFrogspawnFluidState::class.java, null, "vs\$shipFluidSupport", ordinary, local,
            Operation<FluidState> { original }))
    }

    @Test fun `vanilla frogspawn survival accepts only full retained water with dry space above`() {
        val getter = mockk<BlockGetter>()
        every { getter.getFluidState(any()) } answers {
            val pos = firstArg<BlockPos>()
            invoke(MixinFrogspawnFluidState::class.java, null, "vs\$shipFluidSupport", level, pos,
                Operation<FluidState> { level.getBlockState(pos).fluidState }) as FluidState
        }
        for (amount in listOf(255, 254, 0)) {
            fill = amount; sequence++
            assertEquals(amount == 255, invoke(net.minecraft.world.level.block.FrogspawnBlock::class.java,
                null, "mayPlaceOn", getter, local))
        }
        fill = 255; sequence++
        every { level.getBlockState(local.above()) } returns Blocks.WATER.defaultBlockState()
        assertEquals(false, invoke(net.minecraft.world.level.block.FrogspawnBlock::class.java,
            null, "mayPlaceOn", getter, local))
    }
}
