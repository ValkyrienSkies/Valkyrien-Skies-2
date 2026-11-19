package org.valkyrienskies.mod.common.util

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import net.minecraft.SharedConstants
import net.minecraft.network.protocol.Packet
import net.minecraft.resources.ResourceKey
import net.minecraft.server.Bootstrap
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import org.joml.Vector3d
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import org.valkyrienskies.mod.mixin.server.MixinPlayerList
import org.valkyrienskies.mod.mixin.world.entity.MixinPlayer
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck
import java.util.IdentityHashMap
import java.util.UUID

class MinecraftPlayerTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @AfterEach
    fun cleanup() = unmockkAll()

    private fun player(uuid: UUID, dimension: String = "overworld"): ServerPlayer {
        val level = mockk<ServerLevel>(moreInterfaces = arrayOf(DimensionIdProvider::class))
        every { (level as DimensionIdProvider).dimensionId } returns dimension
        val player = mockk<ServerPlayer>(relaxed = true, moreInterfaces = arrayOf(PlayerDuck::class))
        every { player.uuid } returns uuid
        every { player.level() } returns level
        var wrapper = MinecraftPlayer(player)
        every { (player as PlayerDuck).vs_getPlayer() } answers { wrapper }
        every { (player as PlayerDuck).vs_setPlayer(any()) } answers { wrapper = firstArg() }
        return player
    }

    private fun wrapper(player: ServerPlayer) = (player as PlayerDuck).vs_getPlayer()

    private fun finishRespawn(oldPlayer: ServerPlayer, newPlayer: ServerPlayer) {
        val mixin = object : MixinPlayerList() {
            override fun broadcast(player: Player?, x: Double, y: Double, z: Double, distance: Double,
                worldKey: ResourceKey<Level>, packet: Packet<*>) {}
        }
        val method = MixinPlayerList::class.java.getDeclaredMethod(
            "afterPlayerRespawn", ServerPlayer::class.java, Boolean::class.javaPrimitiveType,
            CallbackInfoReturnable::class.java,
        ).apply { isAccessible = true }
        method.invoke(mixin, oldPlayer, false, CallbackInfoReturnable("respawn", false, newPlayer))
    }

    @Test
    fun `wrapper initialization waits for the completed Player constructor`() {
        // Unit tests do not apply Mixins. Constructing the untransformed mixin must not eagerly
        // capture Player's temporary Entity UUID; the real initializer belongs at constructor RETURN.
        assertNull(MixinPlayer().vs_getPlayer())
        val initializer = MixinPlayer::class.java.declaredMethods.mapNotNull { it.getAnnotation(Inject::class.java) }
            .single { "<init>" in it.method }
        assertEquals(listOf("RETURN"), initializer.at.map { it.value })
    }

    @Test
    fun `respawn retains wrapper identity and tracking while following the replacement entity`() {
        val uuid = UUID.randomUUID()
        val original = player(uuid, "nether")
        val connection = mockk<ServerGamePacketListenerImpl>()
        original.connection = connection
        val wrapped = wrapper(original)
        val originalHash = wrapped.hashCode()
        wrapped.forceWatchingShips.add(42L)
        val tracked = hashMapOf(wrapped to setOf(42L))
        val identityTracked = IdentityHashMap<MinecraftPlayer, Long>().apply { put(wrapped, 42L) }
        val previousPlayers = setOf(wrapped)
        var current = original

        repeat(3) {
            val replacement = player(uuid)
            replacement.connection = connection
            every { replacement.x } returns 10.0 + it
            every { replacement.y } returns 64.0
            every { replacement.z } returns 20.0
            finishRespawn(current, replacement)

            assertSame(wrapped, wrapper(replacement))
            assertSame(replacement, wrapped.player)
            assertSame(replacement, tracked.keys.single().playerEntityReference.get())
            assertEquals(uuid, wrapped.uuid)
            assertEquals(originalHash, wrapped.hashCode())
            assertEquals("overworld", wrapped.dimension)
            assertEquals(Vector3d(10.0 + it, 64.0, 20.0), wrapped.getPosition(Vector3d()))
            assertEquals(setOf(42L), wrapped.forceWatchingShips)
            assertEquals(42L, identityTracked[wrapper(replacement)])
            // Core's newPlayers calculation must not request another full body-create snapshot.
            assertTrue((setOf(wrapper(replacement)) - previousPlayers).isEmpty())
            current = replacement
        }
    }

    @Test
    fun `same account reconnect between ticks still gets a fresh tracking identity`() {
        val uuid = UUID.randomUUID()
        val previous = player(uuid)
        val joined = player(uuid)
        previous.connection = mockk()
        joined.connection = mockk()
        val freshWrapper = wrapper(joined)

        finishRespawn(previous, joined)

        assertSame(freshWrapper, wrapper(joined))
        assertNotEquals(wrapper(previous), freshWrapper)
        assertEquals(wrapper(previous).uuid, freshWrapper.uuid)
        assertEquals(setOf(freshWrapper), setOf(freshWrapper) - setOf(wrapper(previous)))
        assertSame(previous, wrapper(previous).player)
    }

    @Test
    fun `rebind rejects a different account without changing identity or reference`() {
        val original = player(UUID.randomUUID())
        val wrapped = wrapper(original)
        val hash = wrapped.hashCode()

        assertThrows(IllegalArgumentException::class.java) { wrapped.rebindPlayer(player(UUID.randomUUID())) }

        assertSame(original, wrapped.player)
        assertEquals(original.uuid, wrapped.uuid)
        assertEquals(hash, wrapped.hashCode())
    }

    @Test
    fun `position snapshot does not mix entities when a rebind occurs during the read`() {
        val uuid = UUID.randomUUID()
        val original = player(uuid)
        val replacement = player(uuid)
        val wrapped = wrapper(original)
        every { original.x } answers { wrapped.rebindPlayer(replacement); 1.0 }
        every { original.y } returns 2.0
        every { original.z } returns 3.0
        every { replacement.y } returns 200.0
        every { replacement.z } returns 300.0

        assertEquals(Vector3d(1.0, 2.0, 3.0), wrapped.getPosition(Vector3d()))
        assertSame(replacement, wrapped.player)
    }

    @Test
    fun `returning the same player leaves the session intact`() {
        val player = player(UUID.randomUUID())
        val wrapped = wrapper(player)

        finishRespawn(player, player)

        assertSame(wrapped, wrapper(player))
        assertSame(player, wrapped.player)
    }
}
