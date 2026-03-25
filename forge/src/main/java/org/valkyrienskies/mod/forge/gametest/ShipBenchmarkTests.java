package org.valkyrienskies.mod.forge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

import java.util.HashSet;
import java.util.Set;

@GameTestHolder(ValkyrienSkiesMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ShipBenchmarkTests {

    private static final Logger LOGGER = LoggerFactory.getLogger("VS2Benchmark");
    private static final int SHIP_COUNT = 100;
    private static final int TICK_COUNT = 100;

    /**
     * Benchmark: Spawns 1000 ships by repeatedly placing and assembling a block,
     * then measures the time for 100 subsequent server ticks with all ships loaded.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 60000)
    public void benchmarkSpawn1000ShipsAnd100Ticks(GameTestHelper helper) {
        // Wait 1 tick for the test area to settle
        helper.runAfterDelay(1, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos placePos = new BlockPos(1, 1, 1);
            BlockPos absolutePos = helper.absolutePos(placePos);

            // Phase 1: Spawn 1000 ships
            long spawnStartNanos = System.nanoTime();
            int shipsSpawned = 0;

            for (int i = 0; i < SHIP_COUNT; i++) {
                // Place a stone block (reuse same position since assembly removes it)
                helper.setBlock(placePos, Blocks.STONE.defaultBlockState());

                Set<BlockPos> blocks = new HashSet<>();
                blocks.add(absolutePos);

                try {
                    ShipAssembler.assembleToShip(level, blocks, 1.0);
                    shipsSpawned++;
                } catch (Exception e) {
                    helper.fail("Failed to assemble ship " + (i + 1) + ": " + e.getMessage());
                    return;
                }
            }

            long spawnElapsedNanos = System.nanoTime() - spawnStartNanos;
            double spawnMs = spawnElapsedNanos / 1_000_000.0;

            LOGGER.info("========================================");
            LOGGER.info("VS2 BENCHMARK: Ship Spawning");
            LOGGER.info("========================================");
            LOGGER.info("Ships spawned:    {}", shipsSpawned);
            LOGGER.info("Total spawn time: {}", String.format("%.2f ms", spawnMs));
            LOGGER.info("Avg per ship:     {}", String.format("%.2f ms", spawnMs / shipsSpawned));
            LOGGER.info("Throughput:       {}", String.format("%.1f ships/sec", shipsSpawned / (spawnMs / 1000.0)));
            LOGGER.info("========================================");
            LOGGER.info("Now ticking {} ticks with {} ships loaded...", TICK_COUNT, shipsSpawned);

            // Phase 2: Let the server tick 100 times with all ships loaded
            final int finalShipsSpawned = shipsSpawned;
            final double finalSpawnMs = spawnMs;
            long tickStartNanos = System.nanoTime();

            helper.runAfterDelay(TICK_COUNT, () -> {
                long tickElapsedNanos = System.nanoTime() - tickStartNanos;
                double tickMs = tickElapsedNanos / 1_000_000.0;
                double totalMs = finalSpawnMs + tickMs;

                LOGGER.info("========================================");
                LOGGER.info("VS2 BENCHMARK: Tick Performance");
                LOGGER.info("========================================");
                LOGGER.info("Ticks elapsed:    {}", TICK_COUNT);
                LOGGER.info("Ships loaded:     {}", finalShipsSpawned);
                LOGGER.info("Total tick time:  {}", String.format("%.2f ms", tickMs));
                LOGGER.info("Avg per tick:     {}", String.format("%.2f ms", tickMs / TICK_COUNT));
                LOGGER.info("Effective TPS:    {}", String.format("%.1f", TICK_COUNT / (tickMs / 1000.0)));
                LOGGER.info("========================================");
                LOGGER.info("VS2 BENCHMARK: Summary");
                LOGGER.info("========================================");
                LOGGER.info("Spawn {} ships:   {}", finalShipsSpawned, String.format("%.2f ms", finalSpawnMs));
                LOGGER.info("Run {} ticks:     {}", TICK_COUNT, String.format("%.2f ms", tickMs));
                LOGGER.info("Total benchmark:  {}", String.format("%.2f ms", totalMs));
                LOGGER.info("========================================");

                helper.succeed();
            });
        });
    }

    /**
     * Baseline benchmark: Measures 100 server ticks with zero ships loaded.
     * Use this to compare against the 1000-ship benchmark to isolate VS2 overhead.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 60000)
    public void benchmarkBaseline100Ticks(GameTestHelper helper) {
        helper.runAfterDelay(1, () -> {
            LOGGER.info("========================================");
            LOGGER.info("VS2 BASELINE: Starting 0-ship tick measurement");
            LOGGER.info("========================================");

            long tickStartNanos = System.nanoTime();

            helper.runAfterDelay(TICK_COUNT, () -> {
                long tickElapsedNanos = System.nanoTime() - tickStartNanos;
                double tickMs = tickElapsedNanos / 1_000_000.0;

                LOGGER.info("========================================");
                LOGGER.info("VS2 BASELINE: Tick Performance (0 ships)");
                LOGGER.info("========================================");
                LOGGER.info("Ticks elapsed:    {}", TICK_COUNT);
                LOGGER.info("Ships loaded:     0");
                LOGGER.info("Total tick time:  {}", String.format("%.2f ms", tickMs));
                LOGGER.info("Avg per tick:     {}", String.format("%.2f ms", tickMs / TICK_COUNT));
                LOGGER.info("Effective TPS:    {}", String.format("%.1f", TICK_COUNT / (tickMs / 1000.0)));
                LOGGER.info("========================================");

                helper.succeed();
            });
        });
    }
}
