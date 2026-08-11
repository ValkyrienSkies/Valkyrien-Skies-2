package org.valkyrienskies.mod.forge.autotest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;
import org.valkyrienskies.core.internal.physics.VsiFluidTopologySnapshot;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.core.util.datastructures.DenseBlockPosSet;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssemblyKt;
import org.valkyrienskies.mod.common.fluid.FloodedFluidClientCache;
import org.valkyrienskies.mod.common.fluid.FluidTopologyClientCache;

/**
 * Script-driven in-game test harness with screenshot capture.
 *
 * <p>Inert unless the JVM property {@code vs.autotest} names a script file, wired through the
 * {@code vs_autotest} Gradle property — see {@code forge/build.gradle} and {@code autotest/run.sh},
 * which launches the client on a virtual display so no window opens on the developer's desktop.</p>
 *
 * <p>The script is line-based; {@code #} starts a comment. One instruction runs per client tick
 * (except {@code wait}s). Instructions:</p>
 * <pre>
 *   world &lt;name&gt;          create a fresh superflat creative world
 *   wait-level              block until the player exists and no screen is open
 *   wait &lt;ticks&gt;          idle N client ticks
 *   cmd &lt;command&gt;         run a command as the player (no leading slash)
 *   chat &lt;message&gt;        send chat
 *   look &lt;yaw&gt; &lt;pitch&gt;  set the player's rotation
 *   look-at &lt;x&gt; &lt;y&gt; &lt;z&gt; aim the player at a world position
 *   run &lt;hook args...&gt;    invoke a named Java hook (see below)
 *   screenshot &lt;name&gt;     capture the framebuffer to screenshots/&lt;name&gt;.png
 *   log &lt;message&gt;         marker line into the log
 *   quit                    stop the client
 * </pre>
 *
 * <p>Hooks cover setup that has no command equivalent:</p>
 * <pre>
 *   spawn_ship &lt;block&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;     assemble a hollow box of that block into a ship
 *   shipinfo                                       log every loaded ship's id and position
 *   flood_report &lt;tag&gt;                          log the client's flood/topology snapshot state
 * </pre>
 *
 * <p>{@code flood_report} is the one that answers "is flooding actually reaching the client": it
 * reports, per ship, whether a topology snapshot exists, whether it is enabled and valid, and how
 * many flooded voxels arrived. Nothing renders without those, so an empty report separates a
 * synchronization problem from a rendering one.</p>
 *
 * <p>Every failure captures an {@code error.png}, logs the cause, and records the failure in
 * {@code autotest-result.txt} so the calling script fails loudly rather than hanging.</p>
 */
public final class AutoTestHarness {

    private static final Logger LOGGER = LogManager.getLogger("VS Autotest");
    private static final int WAIT_LEVEL_TIMEOUT_TICKS = 20 * 60 * 5;
    /** How long {@link Minecraft#stop()} gets to shut down cleanly before the watchdog halts the JVM. */
    private static final long EXIT_GRACE_MILLIS = 5_000L;

    private static ServerShip lastSpawnedShip;

    private List<String[]> instructions;
    private int pc;
    private int waitTicks;
    private boolean waitingForLevel;
    private int waitedForLevel;
    private boolean started;
    private boolean done;

    private AutoTestHarness() {
    }

    /** Installs the harness if {@code vs.autotest} is set; called once from client bootstrap. */
    public static void install() {
        final String script = System.getProperty("vs.autotest");
        if (script == null || script.isBlank()) {
            return;
        }
        final AutoTestHarness harness = new AutoTestHarness();
        try {
            final List<String[]> parsed = new ArrayList<>();
            for (String line : Files.readAllLines(Path.of(script))) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                parsed.add(line.split("\\s+"));
            }
            harness.instructions = parsed;
        } catch (final IOException e) {
            throw new IllegalStateException("autotest script unreadable: " + script, e);
        }
        MinecraftForge.EVENT_BUS.addListener(harness::onClientTick);
        LOGGER.info("[autotest] installed with {} instructions from {}", harness.instructions.size(), script);
    }

    private void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.done) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();

        // Hold until the game is interactive (loading overlay gone), so `world` fires from the title screen.
        if (!this.started) {
            if (minecraft.getOverlay() != null) {
                return;
            }
            this.started = true;
            // The run has no one at the keyboard, so the window routinely sits unfocused. Left alone,
            // vanilla opens the pause menu, which halts the script and puts the menu in every
            // screenshot from then on.
            minecraft.options.pauseOnLostFocus = false;
            LOGGER.info("[autotest] starting script");
        }

        try {
            this.step(minecraft);
        } catch (final Throwable t) {
            LOGGER.error("[autotest] FAILED at instruction {} of {}", this.pc, this.instructions.size(), t);
            this.finish(minecraft, "FAIL instruction " + this.pc + ": " + t);
        }
    }

    private void step(final Minecraft minecraft) {
        if (this.waitTicks > 0) {
            this.waitTicks--;
            return;
        }
        if (this.waitingForLevel) {
            if (minecraft.player != null && minecraft.level != null && minecraft.screen == null) {
                this.waitingForLevel = false;
                LOGGER.info("[autotest] level ready after {} ticks", this.waitedForLevel);
            } else if (++this.waitedForLevel > WAIT_LEVEL_TIMEOUT_TICKS) {
                throw new IllegalStateException("world never became ready (screen=" + minecraft.screen + ")");
            } else {
                return;
            }
        }
        if (this.pc >= this.instructions.size()) {
            this.finish(minecraft, "OK");
            return;
        }

        final String[] inst = this.instructions.get(this.pc++);
        LOGGER.info("[autotest] [{}] {}", this.pc - 1, String.join(" ", inst));
        switch (inst[0].toLowerCase(Locale.ROOT)) {
            case "world" -> this.createWorld(minecraft, inst[1]);
            case "wait-level" -> {
                this.waitingForLevel = true;
                this.waitedForLevel = 0;
            }
            case "wait" -> this.waitTicks = Integer.parseInt(inst[1]);
            case "cmd" -> minecraft.player.connection.sendCommand(join(inst, 1));
            case "chat" -> minecraft.player.connection.sendChat(join(inst, 1));
            case "look" -> {
                minecraft.player.setYRot(Float.parseFloat(inst[1]));
                minecraft.player.setXRot(Float.parseFloat(inst[2]));
            }
            case "look-at" -> lookAt(minecraft,
                Double.parseDouble(inst[1]), Double.parseDouble(inst[2]), Double.parseDouble(inst[3]));
            case "run" -> runHook(minecraft, inst);
            case "screenshot" -> screenshot(minecraft, inst[1]);
            case "log" -> LOGGER.info("[autotest] MARK: {}", join(inst, 1));
            case "quit" -> this.finish(minecraft, "OK");
            default -> throw new IllegalArgumentException("unknown instruction: " + inst[0]);
        }
    }

    private static String join(final String[] parts, final int from) {
        return String.join(" ", List.of(parts).subList(from, parts.length));
    }

    private void createWorld(final Minecraft minecraft, final String name) {
        final LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
            new GameRules(), WorldDataConfiguration.DEFAULT);
        final WorldOptions options = new WorldOptions(0L, false, false);
        minecraft.createWorldOpenFlows().createFreshLevel(name, settings, options,
            access -> access.registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        this.waitingForLevel = true;
        this.waitedForLevel = 0;
    }

    private static void runHook(final Minecraft minecraft, final String[] inst) {
        switch (inst[1]) {
            case "spawn_ship" -> spawnShip(minecraft, blockByName(inst[2]),
                Integer.parseInt(inst[3]), Integer.parseInt(inst[4]), Integer.parseInt(inst[5]));
            case "shipinfo" -> logShipInfo(minecraft);
            case "flood_report" -> floodReport(minecraft, inst.length > 2 ? inst[2] : "");
            case "pin_ship" -> pinLastShip(minecraft);
            default -> throw new IllegalArgumentException("unknown hook: " + inst[1]);
        }
    }

    private static Block blockByName(final String id) {
        final Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (block == null || block == Blocks.AIR) {
            throw new IllegalArgumentException("unknown block: " + id);
        }
        return block;
    }

    /**
     * Builds a hollow 5x4x5 box of {@code block} centred on the given position and assembles it into a
     * ship through the same path the mod's own assembly uses.
     *
     * <p>Hollow on purpose: a solid cube has no interior for fluid to occupy, so it can never produce a
     * flood snapshot and would make a flooding test vacuously empty.</p>
     */
    private static void spawnShip(final Minecraft minecraft, final Block block, final int x, final int y, final int z) {
        final MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("spawn_ship: no integrated server");
        }
        final ResourceKey<Level> dimension = minecraft.level.dimension();
        server.execute(() -> {
            final ServerLevel level = server.getLevel(dimension);
            final DenseBlockPosSet blocks = new DenseBlockPosSet();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = 0; dy <= 3; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        final boolean shell = dx == -2 || dx == 2 || dz == -2 || dz == 2 || dy == 0;
                        if (!shell) {
                            continue;
                        }
                        final BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                        level.setBlock(pos, block.defaultBlockState(), 3);
                        blocks.add(pos.getX(), pos.getY(), pos.getZ());
                    }
                }
            }
            final ServerShip ship =
                ShipAssemblyKt.createNewShipWithBlocks(new BlockPos(x, y, z), blocks, level);
            lastSpawnedShip = ship;
            LOGGER.info("[autotest] spawned ship id={} at ({}, {}, {})", ship.getId(), x, y, z);
        });
    }

    /**
     * Makes the last spawned ship static, so it holds the position it was assembled at.
     *
     * <p>A freshly assembled hull is an ordinary dynamic body: left alone it sinks or drifts within a
     * few seconds, which is fatal for a test that needs it at a specific waterline when the
     * screenshots are taken. Pinning it removes the timing dependence entirely.</p>
     */
    private static void pinLastShip(final Minecraft minecraft) {
        final MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("pin_ship: no integrated server");
        }
        final ServerShip ship = lastSpawnedShip;
        if (ship == null) {
            throw new IllegalStateException("pin_ship: no ship has been spawned yet");
        }
        server.execute(() -> {
            ship.setStatic(true);
            LOGGER.info("[autotest] pinned ship id={} (static)", ship.getId());
        });
    }

    private static void logShipInfo(final Minecraft minecraft) {
        final VsiClientShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(minecraft.level);
        int count = 0;
        for (final ClientShip ship : shipWorld.getLoadedShips()) {
            count++;
            LOGGER.info("[autotest] ship id={} aabb={}", ship.getId(), ship.getRenderAABB());
        }
        LOGGER.info("[autotest] shipinfo: {} loaded ship(s)", count);
    }

    /**
     * Reports whether flood data actually reached the client, per ship.
     *
     * <p>Both renderers are downstream of these two snapshots, so this distinguishes "the server never
     * sent flooding" from "it arrived and did not draw" — which are opposite bugs.</p>
     */
    private static void floodReport(final Minecraft minecraft, final String tag) {
        final VsiClientShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(minecraft.level);
        int ships = 0;
        for (final ClientShip ship : shipWorld.getLoadedShips()) {
            ships++;
            final FluidTopologyClientCache.CachedSnapshot topology =
                FluidTopologyClientCache.get(shipWorld, ship.getId());
            final FloodedFluidClientCache.CachedSnapshot flooding =
                FloodedFluidClientCache.get(shipWorld, ship.getId());

            if (topology == null) {
                LOGGER.info("[autotest] flood_report{} ship={} topology=ABSENT flooding={}",
                    tagSuffix(tag), ship.getId(), flooding == null ? "ABSENT" : "present");
                continue;
            }
            final VsiFluidTopologySnapshot topSnap = topology.getSnapshot();
            int flooded = 0;
            int maxFill = 0;
            if (flooding != null) {
                for (final VsiFluidFloodedVoxel voxel : flooding.getSnapshot().getVoxels()) {
                    flooded++;
                    maxFill = Math.max(maxFill, voxel.getFillAmount());
                }
            }
            LOGGER.info(
                "[autotest] flood_report{} ship={} topology(enabled={} valid={} voxels={} domain={}) "
                    + "flooding(voxels={} maxFill={})",
                tagSuffix(tag), ship.getId(), topSnap.getEnabled(), topSnap.getValid(),
                topSnap.getVoxels().size(), topSnap.getTotalDomainVoxelCount(), flooded, maxFill);
        }
        LOGGER.info("[autotest] flood_report{}: {} loaded ship(s)", tagSuffix(tag), ships);
    }

    private static String tagSuffix(final String tag) {
        return tag.isEmpty() ? "" : "[" + tag + "]";
    }

    private static void lookAt(final Minecraft minecraft, final double x, final double y, final double z) {
        final Vec3 eye = minecraft.player.getEyePosition();
        final double dx = x - eye.x;
        final double dy = y - eye.y;
        final double dz = z - eye.z;
        final double horizontal = Math.sqrt(dx * dx + dz * dz);
        minecraft.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        minecraft.player.setXRot((float) Math.toDegrees(-Math.atan2(dy, horizontal)));
    }

    private static void screenshot(final Minecraft minecraft, final String name) {
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(),
            component -> LOGGER.info("[autotest] screenshot {}: {}", name, component.getString()));
    }

    private void finish(final Minecraft minecraft, final String result) {
        if (this.done) {
            return;
        }
        this.done = true;
        LOGGER.info("[autotest] finished: {}", result);
        if (!result.startsWith("OK")) {
            screenshot(minecraft, "error");
        }
        try {
            Files.writeString(minecraft.gameDirectory.toPath().resolve("autotest-result.txt"), result + "\n");
        } catch (final IOException e) {
            LOGGER.error("[autotest] could not write result file", e);
        }
        minecraft.execute(minecraft::stop);
        forceExitAfterGracePeriod();
    }

    /**
     * Guarantees the JVM actually dies after {@link Minecraft#stop()}.
     *
     * <p>A clean shutdown regularly does not happen: the client's own teardown can wedge on a non-daemon
     * thread that never joins, or a GL context teardown that blocks. A run that never returns is worse
     * than a rough exit, and the result file and screenshots are already on disk by this point, so a
     * daemon watchdog halts the VM once the grace period is up. {@link Runtime#halt} rather than
     * {@code System.exit} deliberately: exit runs shutdown hooks, which is the machinery that hangs.</p>
     */
    private static void forceExitAfterGracePeriod() {
        final Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(EXIT_GRACE_MILLIS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // Nothing is logged here on purpose: Forge routes System.out through log4j, and a shutdown
            // that has already wedged is exactly the state in which writing to it blocks.
            Runtime.getRuntime().halt(0);
        }, "autotest-exit-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }
}
