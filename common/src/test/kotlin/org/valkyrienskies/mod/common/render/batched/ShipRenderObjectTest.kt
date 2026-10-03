package org.valkyrienskies.mod.common.render.batched

import com.mojang.blaze3d.vertex.VertexBuffer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.SharedConstants
import net.minecraft.client.multiplayer.ClientChunkCache
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.block.BlockRenderDispatcher
import net.minecraft.core.SectionPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunkSection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.properties.IShipActiveChunksSet
import org.valkyrienskies.core.api.util.functions.IntBinaryConsumer
import org.valkyrienskies.mod.mixinducks.client.world.ClientChunkCacheDuck

class ShipRenderObjectTest {
    @Test
    fun `chunk arrival and refresh recover without dropping geometry while data is missing`() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val chunks = Long2ObjectOpenHashMap<LevelChunk>()
        val chunkSource = mockk<ClientChunkCache>(moreInterfaces = arrayOf(ClientChunkCacheDuck::class))
        every { (chunkSource as ClientChunkCacheDuck).`vs$getShipChunks`() } returns chunks
        var tick = 0L
        val level = mockk<ClientLevel>()
        // MockK 1.12 does not intercept this covariant getter; it reads the backing field.
        ClientLevel::class.java.getDeclaredField("chunkSource").apply { isAccessible = true }.set(level, chunkSource)
        every { level.gameTime } answers { tick }
        every { level.minSection } returns 0
        every { level.maxSection } returns 2
        every { level.getSectionIndexFromSectionY(any()) } answers { firstArg() }
        val activeChunks = mockk<IShipActiveChunksSet>()
        every { activeChunks.forEach(any()) } answers { firstArg<IntBinaryConsumer>().accept(0, 0) }
        val ship = mockk<ClientShip>()
        every { ship.activeChunksSet } returns activeChunks
        val objectToRender = ShipRenderObject(ship)
        val dispatcher = mockk<BlockRenderDispatcher>()
        val compiler = mockk<ShipSectionCompiler>()
        val firstMesh = ShipMesh(arrayOfNulls<VertexBuffer>(0), Long2ObjectOpenHashMap(), 0, 0, 0, doubleArrayOf())
        val replacement = ShipMesh(arrayOfNulls<VertexBuffer>(0), Long2ObjectOpenHashMap(), 0, 0, 0, doubleArrayOf())
        every { compiler.compileShip(level, dispatcher, any(), 0, 0, 0) } returnsMany listOf(firstMesh, replacement)

        objectToRender.pollChunks(level)
        objectToRender.markSectionDirty(0, 0, 0)
        assertFalse(objectToRender.compileNextBatch(level, dispatcher, compiler, Long.MAX_VALUE))
        verify(exactly = 0) { compiler.compileShip(any(), any(), any(), any(), any(), any()) }

        val section = mockk<LevelChunkSection>()
        every { section.hasOnlyAir() } returns false
        fun chunk() = mockk<LevelChunk>().also { every { it.getSection(any()) } returns section }
        chunks.put(ChunkPos.asLong(0, 0), chunk())
        tick++
        objectToRender.pollChunks(level)
        assertTrue(objectToRender.compileNextBatch(level, dispatcher, compiler, Long.MAX_VALUE))
        assertSame(firstMesh, objectToRender.meshes.single())

        chunks.clear()
        tick++
        objectToRender.pollChunks(level)
        objectToRender.markSectionDirty(0, 0, 0)
        assertFalse(objectToRender.compileNextBatch(level, dispatcher, compiler, Long.MAX_VALUE))
        assertSame(firstMesh, objectToRender.meshes.single())

        chunks.put(ChunkPos.asLong(0, 0), chunk())
        tick++
        objectToRender.pollChunks(level)
        assertTrue(objectToRender.compileNextBatch(level, dispatcher, compiler, Long.MAX_VALUE))
        assertSame(replacement, objectToRender.meshes.single())
        verify(exactly = 2) {
            compiler.compileShip(level, dispatcher,
                match { it.size == 2 && it.contains(SectionPos.asLong(0, 0, 0)) && it.contains(SectionPos.asLong(0, 1, 0)) },
                0, 0, 0)
        }

        // An in-place packet refresh can empty a chunk without changing its identity.
        every { section.hasOnlyAir() } returns true
        objectToRender.markColumnDirty(level, 0, 0)
        assertTrue(objectToRender.compileNextBatch(level, dispatcher, compiler, Long.MAX_VALUE))
        assertTrue(objectToRender.meshes.isEmpty())
    }
}
