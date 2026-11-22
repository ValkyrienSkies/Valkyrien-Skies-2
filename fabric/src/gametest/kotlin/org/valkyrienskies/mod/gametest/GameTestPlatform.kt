package org.valkyrienskies.mod.gametest

import com.mojang.authlib.GameProfile
import com.simibubi.create.foundation.networking.SimplePacketBase
import java.util.UUID
import net.fabricmc.fabric.api.entity.FakePlayer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

// Fabric side of the shared game tests (gametest/src/main/kotlin). Run with ./gradlew :fabric:runGameTestServer

/** Fabric uses the template string as the structure id. */
const val TEST_AREA = "valkyrienskies:test_area"

// Forge's holder annotations; Fabric registers test classes through the fabric-gametest entrypoint instead.
annotation class GameTestHolder(val value: String)
annotation class PrefixGameTestTemplate(val value: Boolean)

fun createFakePlayer(level: ServerLevel): ServerPlayer = FakePlayer.get(level, GameProfile(UUID.randomUUID(), "vs_gametest"))

fun isModLoaded(id: String) = FabricLoader.getInstance().isModLoaded(id)

/** Runs a Create server-bound packet's handler as if [player] sent it. */
fun handleCreatePacket(packet: SimplePacketBase, player: ServerPlayer) {
    packet.handle(SimplePacketBase.Context(player.server, player.connection, player))
}
