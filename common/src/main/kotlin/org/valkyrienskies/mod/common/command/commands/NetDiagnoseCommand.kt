package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.core.internal.network.NetworkLaneDiagnosis
import org.valkyrienskies.core.internal.network.NetworkLaneDiagnosis.State
import org.valkyrienskies.mod.common.playerWrapper
import org.valkyrienskies.mod.common.vsCore

/**
 * `/vs net diagnose [player]`: whether players use VS's UDP connection, and if not, why and what to change.
 *
 * The server can't test its own UDP port from outside, so player results come from the players' games: each tries
 * UDP when it joins (and later again while it has none) and reports back how it went.
 *
 * - Any player can run `/vs net diagnose` to see their own connection.
 * - Operators (and the console) also see the server's UDP setup and every player.
 * - `/vs net diagnose <player>` (operators) has that player's game try again now and shows the result.
 *
 * Each player gets a message of their own, so a busy server's report never exceeds a chat message's size limit.
 */
object NetDiagnoseCommand {
    private const val OP = 2
    private const val OK = "✔"
    private const val BAD = "✘"
    private const val WAIT = "…"

    fun register(net: LiteralArgumentBuilder<CommandSourceStack>) {
        net.then(
            literal("diagnose")
                .executes { diagnose(it.source); 1 }
                .then(argument("player", EntityArgument.player())
                    .requires { it.hasPermission(OP) }
                    .executes { test(it.source, EntityArgument.getPlayer(it, "player")); 1 })
        )
    }

    private fun diagnose(source: CommandSourceStack) {
        val host = vsCore.networkHost
        val text = Component.literal("[VS] UDP check").withStyle(ChatFormatting.GOLD)
        val players = ArrayList<Component>()
        if (source.hasPermission(OP)) {
            text.append(heading("Server"))
            for (item in host.describeNetworkSetup()) {
                text.append(field("  ", item.label, item.value, item.ok?.let { if (it) ChatFormatting.GREEN else ChatFormatting.RED }))
            }
            val online = source.server.playerList.players
            text.append(heading("Players"))
            text.append(Component.literal(" (each player's game tests UDP and reports back)").withStyle(ChatFormatting.DARK_GRAY))
            if (online.isEmpty()) {
                text.append(field("  ", "None online", "join from another network (e.g. a phone hotspot) to test"))
            }
            for (player in online) players += player(player.gameProfile.name, host.diagnoseNetworkLane(player.playerWrapper), op = true)
        } else {
            val player = source.player
            if (player == null) {
                text.append(field("  ", "Server", "only operators can see the setup"))
            } else {
                players += player("You", host.diagnoseNetworkLane(player.playerWrapper), op = false)
            }
        }
        source.sendSuccess({ text }, false)
        // RCON joins a command's messages without line breaks.
        val separator = if (isRcon(source)) "\n" else ""
        for (lines in players) source.sendSuccess({ Component.literal(separator).append(lines) }, false)
    }

    private fun test(source: CommandSourceStack, player: ServerPlayer) {
        val name = player.gameProfile.name
        val server = source.server
        // RCON only returns what a command prints while it runs, so the result goes to the server console instead.
        val rcon = isRcon(source)
        val asking = Component.literal("[VS] Asking $name's game to try UDP again $WAIT").withStyle(ChatFormatting.GOLD)
        if (rcon) {
            asking.append(field("  ", "Result", "in the server console (RCON gets no late replies), or run /vs net diagnose in 20 s"))
        }
        source.sendSuccess({ asking }, false)
        vsCore.networkHost.testNetworkLane(player.playerWrapper) { result ->
            server.execute {
                val text = Component.literal("[VS] UDP check\n").withStyle(ChatFormatting.GOLD).append(player(name, result, op = true))
                if (rcon) server.sendSystemMessage(text) else source.sendSuccess({ text }, false)
            }
        }
    }

    /** `✔ Alex  UDP  via …`, then one indented line per address, fix, note, and the retry. */
    private fun player(name: String, d: NetworkLaneDiagnosis, op: Boolean): MutableComponent {
        val (mark, label, color) = when (d.state) {
            State.UDP -> Triple(OK, "UDP", ChatFormatting.GREEN)
            State.CONNECTING -> Triple(WAIT, "connecting", ChatFormatting.YELLOW)
            State.TCP_ONLY -> Triple(BAD, "TCP only", ChatFormatting.RED)
            State.LOCAL -> Triple("-", "local", ChatFormatting.GRAY)
        }
        val text = Component.literal("  $mark ").withStyle(color)
        text.append(Component.literal(name).withStyle(ChatFormatting.WHITE))
            .append(Component.literal("  $label").withStyle(color))
            .append(Component.literal("  ${d.reason}").withStyle(ChatFormatting.GRAY))
        for (a in d.addresses) {
            val (m, c) = when (a.result.ok) {
                true -> OK to ChatFormatting.GREEN
                false -> BAD to ChatFormatting.RED
                null -> "-" to ChatFormatting.GRAY
            }
            text.append(Component.literal("\n      $m ").withStyle(c))
                .append(Component.literal(a.address).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  ${a.source}  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(a.result.label).withStyle(c))
        }
        d.error?.let { text.append(field("      ", "Error", it)) }
        val fixes = if (op) d.fixes else listOfNotNull(d.playerFix)
        for (fix in fixes) text.append(field("      ", "Fix", fix, ChatFormatting.GOLD))
        if (op) d.playerFix?.let { text.append(field("      ", "Player fix", it, ChatFormatting.GOLD)) }
        if (!op && d.state == State.TCP_ONLY && d.playerFix == null) {
            text.append(field("      ", "Fix", "server setup; an operator can run /vs net diagnose", ChatFormatting.GOLD))
        }
        for (note in d.notes) text.append(field("      ", "Note", note))
        if (d.retryInSeconds != null || (op && d.canRetry)) {
            val line = Component.literal("\n      Retry  ").withStyle(ChatFormatting.DARK_GRAY)
            d.retryInSeconds?.let { line.append(Component.literal("auto in ${duration(it)}  ").withStyle(ChatFormatting.WHITE)) }
            if (op && d.canRetry) {
                line.append(Component.literal("[now]").withStyle {
                    it.withColor(ChatFormatting.AQUA)
                        .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vs net diagnose $name"))
                        .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/vs net diagnose $name")))
                })
            }
            text.append(line)
        }
        return text
    }

    private fun isRcon(source: CommandSourceStack) = source.entity == null && source.textName == "Rcon"

    private fun duration(seconds: Long) = if (seconds < 60) "${seconds}s" else "${(seconds + 30) / 60} min"

    private fun heading(text: String): MutableComponent = Component.literal("\n$text").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)

    private fun field(indent: String, label: String, value: String, valueColor: ChatFormatting? = null): Component =
        Component.literal("\n$indent$label  ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal(value).withStyle(valueColor ?: ChatFormatting.WHITE))
}
