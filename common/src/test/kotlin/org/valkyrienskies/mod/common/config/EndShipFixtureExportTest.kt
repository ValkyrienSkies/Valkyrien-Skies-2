package org.valkyrienskies.mod.common.config

import com.google.gson.JsonParser
import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtUtils
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.Bootstrap
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.primitives.AABBic
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.valkyrienskies.core.api.physics.blockstates.BoxesBlockShape
import org.valkyrienskies.core.internal.VsiCoreFactory
import org.valkyrienskies.core.internal.hooks.VsiCoreHooksOut
import org.valkyrienskies.core.internal.physics.blockstates.VsiBlockState
import org.valkyrienskies.mod.common.BlockStateInfo
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixin.accessors.world.level.block.SlabBlockAccessor
import org.valkyrienskies.mod.mixin.accessors.world.level.block.StairBlockAccessor
import java.nio.file.Files
import java.nio.file.Path

class EndShipFixtureExportTest {
    @TempDir lateinit var configDir: Path

    @Test fun `export template through the real VS2 block registration`() {
        val destination = System.getenv("VS_END_SHIP_FIXTURE")
        assumeTrue(destination != null, "Set VS_END_SHIP_FIXTURE to export the native benchmark fixture")
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val template = requireNotNull(javaClass.getResourceAsStream("/data/minecraft/structures/end_city/ship.nbt"))
            .use { NbtIo.readCompressed(it) }
        val palette = template.getList("palette", 10).map {
            NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), it as net.minecraft.nbt.CompoundTag)
        }.toMutableList()
        val terrain = listOf(Blocks.BEDROCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState())
        palette.addAll(terrain)
        val registration = MassDatapackResolver::class.java.getDeclaredField("registeredBlocks").apply { isAccessible = true }
        val oldRegistration = registration.getBoolean(MassDatapackResolver)
        @Suppress("UNCHECKED_CAST")
        val savedMaps = listOf("map", "mcBlockStateToVs", "liquidIdToFlowingFluid").map { name ->
            val map = MassDatapackResolver::class.java.getDeclaredField(name).apply { isAccessible = true }
                .get(MassDatapackResolver) as MutableMap<Any?, Any?>
            map to map.toMap()
        }
        val hooks = mockk<VsiCoreHooksOut>(relaxed = true)
        every { hooks.configDir } returns configDir
        val core = VsiCoreFactory.instance.newVsCoreServer(hooks)
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        mockkStatic(StairBlockAccessor::class, SlabBlockAccessor::class)
        mockkObject(MassDatapackResolver, recordPrivateCalls = true)
        try {
            every { vsCore } returns core
            @Suppress("UNCHECKED_CAST")
            fun stairs(name: String) = StairBlock::class.java.getDeclaredField(name)
                .apply { isAccessible = true }.get(null) as Array<VoxelShape>
            fun slab(name: String) = SlabBlock::class.java.getDeclaredField(name)
                .apply { isAccessible = true }.get(null) as VoxelShape
            every { StairBlockAccessor.getTopShapes() } returns stairs("TOP_SHAPES")
            every { StairBlockAccessor.getBottomShapes() } returns stairs("BOTTOM_SHAPES")
            every { SlabBlockAccessor.getBottomAABB() } returns slab("BOTTOM_AABB")
            every { SlabBlockAccessor.getTopAABB() } returns slab("TOP_AABB")
            every { MassDatapackResolver["runRegisterBlockStateEvent"]() } returns Unit
            savedMaps.forEach { it.first.clear() }
            val resourceRoot = Path.of("src/main/resources/data/valkyrienskies/vs_mass")
            val resources = Files.walk(resourceRoot).use { paths ->
                paths.filter { it.toString().endsWith(".json") }.sorted().toList().associate { file ->
                    ResourceLocation("valkyrienskies", resourceRoot.relativize(file).toString()
                        .replace('\\', '/').removeSuffix(".json")) to
                        Files.newBufferedReader(file).use { JsonParser.parseReader(it) }
                }
            }
            assertTrue(resources.isNotEmpty())
            val loader = MassDatapackResolver.loader
            loader.javaClass.getDeclaredMethod("apply", Map::class.java, ResourceManager::class.java, ProfilerFiller::class.java)
                .apply { isAccessible = true }.invoke(loader, resources, null, null)
            MassDatapackResolver.registerAllBlockStates(palette)
            @Suppress("UNCHECKED_CAST")
            val registered = savedMaps[1].first as Map<BlockState, VsiBlockState>
            val lines = mutableListOf("VS_END_SHIP 1", template.getList("size", 3).joinToString(" "), palette.size.toString())
            fun box(box: AABBic) = "${box.minX()} ${box.minY()} ${box.minZ()} ${box.maxX()} ${box.maxY()} ${box.maxZ()}"
            var partialStates = 0
            palette.forEachIndexed { index, state ->
                val solid = registered.getValue(state).solidState
                val mass = if (state.isAir) 0.0 else MassDatapackResolver.getBlockStateMass(state) ?: VSGameConfig.SERVER.defaultBlockMass
                lines += "$index $mass ${if (solid == null) 0 else 1}"
                if (solid != null) {
                    val shape = solid.shape as BoxesBlockShape
                    lines += "${solid.friction} ${solid.elasticity} ${solid.hardness}"
                    lines += box(shape.boundingBox)
                    lines += "${shape.positiveBoxes.size} ${shape.negativeBoxes.size} ${shape.collisionPoints.size}"
                    lines += shape.positiveBoxes.map(::box)
                    lines += shape.negativeBoxes.map(::box)
                    lines += shape.collisionPoints.map { "${it.x} ${it.y} ${it.z} ${it.radius}" }
                    val volume = shape.positiveBoxes.sumOf { (it.maxX() - it.minX() + 1) * (it.maxY() - it.minY() + 1) * (it.maxZ() - it.minZ() + 1) }
                    if (volume in 1 until 4096) partialStates++
                    if (state.block is StairBlock || state.block is SlabBlock) {
                        val minecraft = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()
                        for (x in 0..15) for (y in 0..15) for (z in 0..15) {
                            val expected = minecraft.any { it.contains((x + 0.5) / 16, (y + 0.5) / 16, (z + 0.5) / 16) }
                            val actual = shape.positiveBoxes.any { x in it.minX()..it.maxX() && y in it.minY()..it.maxY() && z in it.minZ()..it.maxZ() }
                            assertEquals(expected, actual, "$state at $x,$y,$z")
                        }
                    }
                    println("End ship palette $index: $state mass=$mass boxes=${shape.positiveBoxes.size} volume=$volume/4096 friction=${solid.friction}")
                }
            }
            assertTrue(partialStates >= 20, "The fixture must preserve the actual partial shapes")
            val blocks = template.getList("blocks", 10).map { it as net.minecraft.nbt.CompoundTag }
                .filter { !palette[it.getInt("state")].isAir }
            lines += blocks.size.toString()
            lines += blocks.map { "${it.getList("pos", 3).joinToString(" ")} ${it.getInt("state")}" }
            lines += terrain.joinToString(" ") { palette.indexOf(it).toString() }
            Files.write(Path.of(destination!!), lines)
            Files.writeString(Path.of("$destination.palette.txt"), palette.mapIndexed { i, state -> "$i $state" }.joinToString("\n"))
            println("Exported ${blocks.size} blocks, $partialStates partial palette states to $destination")
        } finally {
            unmockkAll()
            savedMaps.forEach { (map, saved) -> map.clear(); map.putAll(saved) }
            registration.setBoolean(MassDatapackResolver, oldRegistration)
            BlockStateInfo.invalidateCache()
        }
    }
}
