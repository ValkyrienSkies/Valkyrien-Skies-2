package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import org.valkyrienskies.core.internal.networking.VsiNetworkStatistics
import org.valkyrienskies.mod.common.networking.VsNetworkStatistics
import org.valkyrienskies.mod.common.vsCore
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date

/**
 * `/vs net stats|reset|dump|sessions` — diagnostic view of VS network traffic counters ([VsiNetworkStatistics]), for
 * operators; `/vs net diagnose` is [NetDiagnoseCommand].
 *
 * - `stats` prints the top entries by bytes, with per-second rates since the last reset.
 * - `reset` clears all counters.
 * - `dump` writes every entry as CSV to `<server dir>/vs-net-stats-<timestamp>.csv`.
 */
object NetStatsCommand {
    private const val TOP = 20

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        val op = { source: CommandSourceStack -> source.hasPermission(2) }
        val net = literal("net")
            .then(literal("stats").requires(op).executes { printStatistics(it.source); 1 })
            .then(literal("reset").requires(op).executes {
                vsCore.networkStatistics.reset()
                it.source.sendSuccess({ Component.literal("[VS] Network stats reset") }, true)
                1
            })
            .then(literal("dump").requires(op).executes { dump(it.source); 1 })
            .then(literal("sessions").requires(op).executes {
                val text = vsCore.networkHost.describeSessions().trimEnd()
                it.source.sendSuccess({ Component.literal(text) }, false)
                1
            })
        NetDiagnoseCommand.register(net) // open to every player, for their own connection
        vs.then(net)
    }

    private fun elapsedSeconds(stats: VsiNetworkStatistics): Double =
        ((System.currentTimeMillis() - stats.sinceMillis) / 1000.0).coerceAtLeast(0.001)

    private fun printStatistics(source: CommandSourceStack) {
        val stats = vsCore.networkStatistics
        val seconds = elapsedSeconds(stats)
        val entries = stats.snapshot().sortedByDescending { it.bytes }
        val lines = StringBuilder()
        lines.append("[VS] Network stats over %.1fs (%d keys):".format(seconds, entries.size))
        for (e in entries.take(TOP)) {
            lines.append("\n  %-60s %8d pkts %10s  %9s/s  avg %6d B".format(
                e.key, e.packets, human(e.bytes), human((e.bytes / seconds).toLong()),
                if (e.packets == 0L) 0 else e.bytes / e.packets
            ))
        }
        val text = lines.toString()
        source.sendSuccess({ Component.literal(text) }, false)
    }

    private fun dump(source: CommandSourceStack) {
        val stats = vsCore.networkStatistics
        val seconds = elapsedSeconds(stats)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
        val file: Path = source.server.serverDirectory.toPath().resolve("vs-net-stats-$stamp.csv")
        val csv = StringBuilder("key,packets,bytes,bytes_per_s,avg_bytes,est_wire_bytes\n")
        for (e in stats.snapshot().sortedBy { it.key }) {
            // Only wire/* entries correspond to individual MC custom payload packets.
            val estWire = if (e.key.startsWith("wire/")) {
                e.bytes + e.packets * VsNetworkStatistics.MC_CUSTOM_PAYLOAD_OVERHEAD_ESTIMATE
            } else e.bytes
            csv.append("\"${e.key}\",${e.packets},${e.bytes},${"%.1f".format(e.bytes / seconds)}," +
                "${if (e.packets == 0L) 0 else e.bytes / e.packets},$estWire\n")
        }
        csv.append("# elapsed_seconds=%.3f\n".format(seconds))
        Files.writeString(file, csv)
        source.sendSuccess({ Component.literal("[VS] Wrote network stats to $file") }, true)
    }

    private fun human(bytes: Long): String = when {
        bytes >= 1L shl 20 -> "%.2f MiB".format(bytes / 1048576.0)
        bytes >= 1L shl 10 -> "%.1f KiB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
