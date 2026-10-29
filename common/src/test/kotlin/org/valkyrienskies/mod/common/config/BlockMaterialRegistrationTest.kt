package org.valkyrienskies.mod.common.config

import com.google.gson.JsonParser
import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.Bootstrap
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.shapes.Shapes
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.physics.blockstates.BoxesBlockShape
import org.valkyrienskies.core.api.physics.blockstates.SolidState
import org.valkyrienskies.core.internal.VsiCore
import org.valkyrienskies.core.internal.physics.blockstates.VsiBlockState
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.common.BlockStateInfo
import org.valkyrienskies.mod.common.DefaultBlockStateInfoProvider
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixin.accessors.world.level.block.SlabBlockAccessor
import org.valkyrienskies.mod.mixin.accessors.world.level.block.StairBlockAccessor

class BlockMaterialRegistrationTest {
    companion object {
        @BeforeAll @JvmStatic fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    @Test fun `early fallback must not hide registered blue ice dirt and grass materials`() {
        val providers = BlockStateInfo::class.java.getDeclaredField("SORTED_REGISTRY").apply { isAccessible = true }
        val oldProviders = providers.get(BlockStateInfo)
        val registration = MassDatapackResolver::class.java.getDeclaredField("registeredBlocks").apply { isAccessible = true }
        val oldRegistration = registration.getBoolean(MassDatapackResolver)
        @Suppress("UNCHECKED_CAST")
        val savedMaps = listOf("map", "mcBlockStateToVs", "liquidIdToFlowingFluid").map { name ->
            val map = MassDatapackResolver::class.java.getDeclaredField(name).apply { isAccessible = true }
                .get(MassDatapackResolver) as MutableMap<Any?, Any?>
            map to map.toMap()
        }
        val core = mockk<VsiCore>(relaxed = true)
        val shape = mockk<BoxesBlockShape>()
        val fallback = mockk<VsiBlockType>()
        val registered = mutableMapOf<VsiBlockState, VsiBlockType>()
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        mockkStatic(StairBlockAccessor::class, SlabBlockAccessor::class)
        mockkStatic(MassDatapackResolver::class)
        mockkObject(MassDatapackResolver, recordPrivateCalls = true)
        try {
            every { vsCore } returns core
            every { core.blockTypes.solid } returns fallback
            every { core.blockTypes.getType(any()) } answers { registered[firstArg()] }
            every { StairBlockAccessor.getTopShapes() } returns emptyArray()
            every { StairBlockAccessor.getBottomShapes() } returns emptyArray()
            every { SlabBlockAccessor.getBottomAABB() } returns Shapes.empty()
            every { SlabBlockAccessor.getTopAABB() } returns Shapes.empty()
            every { MassDatapackResolver.generateStairCollisionShapes(any()) } returns emptyMap()
            every { MassDatapackResolver.generateShapeFromVoxel(any()) } returns shape
            every { MassDatapackResolver["runRegisterBlockStateEvent"]() } returns Unit
            val shapeBuilder = mockk<BoxesBlockShape.Builder>(relaxed = true)
            every { core.newSolidStateBoxesShapeBuilder() } returns shapeBuilder
            every { shapeBuilder.addCollisionPoints(any()) } returns shapeBuilder
            every { shapeBuilder.addPositiveBox(any()) } returns shapeBuilder
            every { shapeBuilder.build() } returns shape
            every { core.newSolidStateBuilder() } answers {
                var value = -1.0
                val builder = mockk<SolidState.Builder>()
                every { builder.shape(any()) } returns builder
                every { builder.elasticity(any()) } returns builder
                every { builder.hardness(any()) } returns builder
                every { builder.friction(any()) } answers { value = firstArg(); builder }
                every { builder.build() } answers {
                    val captured = value
                    mockk<SolidState> { every { friction } returns captured }
                }
                builder
            }
            providers.set(BlockStateInfo, listOf(MassDatapackResolver, DefaultBlockStateInfoProvider))
            BlockStateInfo.invalidateCache()
            val blocks = listOf(Blocks.BLUE_ICE, Blocks.DIRT, Blocks.GRASS_BLOCK).map { it.defaultBlockState() }
            for (state in blocks) assertSame(fallback, BlockStateInfo.get(state)!!.second)

            val resources = listOf("natural/misc", "natural/dirt").associate { name ->
                val path = "/data/valkyrienskies/vs_mass/$name.json"
                ResourceLocation("valkyrienskies", name) to requireNotNull(javaClass.getResourceAsStream(path))
                    .bufferedReader().use { JsonParser.parseReader(it) }
            }.toMutableMap()
            val loader = MassDatapackResolver.loader
            loader.javaClass.getDeclaredMethod("apply", Map::class.java, ResourceManager::class.java, ProfilerFiller::class.java)
                .apply { isAccessible = true }.invoke(loader, resources, null, null)
            MassDatapackResolver.registerAllBlockStates(blocks)
            MassDatapackResolver.blockStateData.forEach { registered[it] = mockk() }

            for ((index, state) in blocks.withIndex()) {
                val type = requireNotNull(MassDatapackResolver.getBlockStateType(state))
                val material = registered.entries.single { it.value === type }.key.solidState!!
                assertEquals(listOf(0.0, 0.6, 0.5)[index], material.friction)
                assertSame(type, BlockStateInfo.get(state)!!.second, "Cached material for $state")
            }
        } finally {
            unmockkAll()
            savedMaps.forEach { (map, saved) -> map.clear(); map.putAll(saved) }
            registration.setBoolean(MassDatapackResolver, oldRegistration)
            providers.set(BlockStateInfo, oldProviders)
            BlockStateInfo.invalidateCache()
        }
    }
}
