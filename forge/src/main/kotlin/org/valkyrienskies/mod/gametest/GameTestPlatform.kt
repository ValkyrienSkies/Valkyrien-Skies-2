package org.valkyrienskies.mod.gametest

import com.mojang.authlib.GameProfile
import com.simibubi.create.foundation.networking.SimplePacketBase
import java.util.UUID
import net.minecraft.network.Connection
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.fml.ModList
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkEvent

// Forge side of the shared game tests (gametest/src/main/kotlin). Run with ./gradlew -PvsTestMods :forge:runGameTestServer

/** Forge resolves a holder's templates in its namespace. */
const val TEST_AREA = "test_area"

typealias GameTestHolder = net.minecraftforge.gametest.GameTestHolder
typealias PrefixGameTestTemplate = net.minecraftforge.gametest.PrefixGameTestTemplate

fun createFakePlayer(level: ServerLevel): ServerPlayer =
    FakePlayerFactory.get(level, GameProfile(UUID.randomUUID(), "vs_gametest"))

fun isModLoaded(id: String) = ModList.get().isLoaded(id)

/** Runs a Create server-bound packet's handler as if [player] sent it. */
fun handleCreatePacket(packet: SimplePacketBase, player: ServerPlayer) {
    val connection = field(ServerGamePacketListenerImpl::class.java, "connection").get(player.connection) as Connection
    val context = NetworkEvent.Context::class.java
        .getDeclaredConstructor(Connection::class.java, NetworkDirection::class.java, Int::class.javaPrimitiveType)
        .apply { isAccessible = true }
        .newInstance(connection, NetworkDirection.PLAY_TO_SERVER, 0)
    packet.handle(context)
}
