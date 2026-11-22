package org.valkyrienskies.mod.gametest.client

import java.io.File
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import org.valkyrienskies.mod.gametest.TestShip

/** One client test: a ship built on the integrated server, then a check run on the client thread. */
class ClientShipTest(
    val name: String,
    val blocks: Map<BlockPos, BlockState>,
    /** Ship position relative to the player's standing spot. */
    val shipAt: BlockPos,
    val serverSetup: (level: ServerLevel, ship: TestShip, base: BlockPos) -> List<Entity> = { _, _, _ -> emptyList() },
    val check: (mc: Minecraft, ship: TestShip, base: BlockPos) -> Unit
)

class ClientTestFailure(message: String) : RuntimeException(message)

fun clientCheck(condition: Boolean, message: () -> String) {
    if (!condition) throw ClientTestFailure(message())
}

/**
 * Runs [ClientShipTests] in a dev client started with -Dvs.clientTests=true (the loaders' runGameTestClient task):
 * creates a flat creative world, runs every test, writes vs-client-tests.txt and halts with the failure count.
 * Each loader calls [tick] at the end of every client tick.
 */
object ClientShipTestRunner {
    private enum class Step { TITLE, WORLD, WARMUP, SETUP, WAIT, CLEANUP }

    val enabled = System.getProperty("vs.clientTests") == "true"
    private const val SETTLE_TICKS = 60
    private const val TIME_LIMIT_TICKS = 20 * 60 * 10

    private var step = Step.TITLE
    private var wait = 0
    private var totalTicks = 0
    private var index = 0
    private var ship: TestShip? = null
    private var extras: List<Entity> = emptyList()
    private val results = mutableListOf<String>()
    private var failures = 0

    // The player stands here; flat world surface is y = -60.
    private val base = BlockPos(0, -60, 0)

    fun tick(mc: Minecraft) {
        if (!enabled) return
        if (++totalTicks > TIME_LIMIT_TICKS) {
            results += "FAIL runner: timed out in step $step"
            finish(mc, failures + 1)
        }
        val tests = ClientShipTests.all
        when (step) {
            Step.TITLE -> if (mc.level == null && (mc.screen is TitleScreen || mc.screen is AccessibilityOnboardingScreen)) {
                createWorld(mc)
                step = Step.WORLD
            }
            Step.WORLD -> if (mc.player != null && mc.level != null && mc.singleplayerServer != null) {
                wait = 60
                step = Step.WARMUP
            }
            Step.WARMUP -> if (--wait <= 0) step = Step.SETUP
            Step.SETUP -> if (index >= tests.size) finish(mc, failures) else {
                setUp(mc, tests[index])
                wait = SETTLE_TICKS
                step = Step.WAIT
            }
            Step.WAIT -> if (--wait <= 0) {
                val test = tests[index]
                try {
                    test.check(mc, ship!!, base)
                    results += "PASS ${test.name}"
                } catch (e: Throwable) {
                    failures++
                    results += "FAIL ${test.name}: ${e.message ?: e.toString()}"
                }
                println("[VS-CLIENT-TEST] ${results.last()}")
                cleanUp(mc)
                wait = 10
                step = Step.CLEANUP
            }
            Step.CLEANUP -> if (--wait <= 0) {
                index++
                step = Step.SETUP
            }
        }
    }

    private fun createWorld(mc: Minecraft) {
        val name = "vs-client-tests"
        File(mc.gameDirectory, "saves/$name").deleteRecursively()
        val rules = GameRules()
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null)
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null)
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null)
        mc.createWorldOpenFlows().createFreshLevel(
            name,
            LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
            WorldOptions(0L, false, false)
        ) { registries ->
            registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()
        }
    }

    private fun setUp(mc: Minecraft, test: ClientShipTest) {
        val server = mc.singleplayerServer!!
        server.executeBlocking {
            val level = server.overworld()
            val player = server.playerList.players.first()
            player.teleportTo(level, base.x + 0.5, base.y.toDouble(), base.z + 0.5, 0f, 0f)
            player.abilities.flying = true
            player.onUpdateAbilities()
            val s = TestShip.create(level, base.offset(test.shipAt), test.blocks)
            ship = s
            extras = test.serverSetup(level, s, base)
        }
    }

    private fun cleanUp(mc: Minecraft) {
        val server = mc.singleplayerServer!!
        server.executeBlocking {
            extras.forEach { it.discard() }
            ship?.delete()
        }
        ship = null
        extras = emptyList()
    }

    private fun finish(mc: Minecraft, failed: Int) {
        val report = results.joinToString("\n") + "\n${results.size - failed} passed, $failed failed\n"
        File(mc.gameDirectory, "vs-client-tests.txt").writeText(report)
        println("[VS-CLIENT-TEST] DONE $failed failed")
        // halt, not exit: a normal shutdown can hang on GL teardown.
        Runtime.getRuntime().halt(failed)
    }
}
