package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.Util
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.mod.api.toJOML
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.schematic.VdexWrapper

object SchematicCommand {

    val shipsNeedingAttachmentLoad = Long2ObjectOpenHashMap<ByteArray>()

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("schematic")
            .requires { it.hasPermission(2) }
            .then(literal("save")
                .then(argument("ship", ShipArgument.ships())
                    .then(argument("filename", StringArgumentType.word())
                        .executes { ctx ->
                            val ship = ShipArgument.getShip(ctx, "ship") as ServerShip
                            val filename = StringArgumentType.getString(ctx, "filename")

                            val metadata = VdexWrapper.saveShip(ctx.source.level, ship, filename, creator = ctx.source.player?.gameProfile?.name ?: "Unknown")

                            ctx.source.sendSuccess({
                                Component.translatable("command.valkyrienskies.schematic.save.success", metadata.ships.size, filename, metadata.constraints.size)
                            }, true)

                            return@executes 1
                        }
                    )
                )
            ).then(literal("load")
                .then(argument("filename", StringArgumentType.word())
                    .executes { ctx ->
                        val error = VdexWrapper.loadShip(ctx.source.level, ctx.source.position.toJOML(), StringArgumentType.getString(ctx, "filename"))
                        if (error == null) {
                            ctx.source.sendSuccess({
                                Component.translatable("command.valkyrienskies.schematic.load.success")
                            }, false)

                            return@executes 1
                        }

                        ctx.source.sendFailure(error)
                        return@executes 0
                    }))
            .then(literal("open-folder")
                .executes {
                    val server = it.source.level.server
                    if (server.isSingleplayer) {
                        Util.getPlatform().openFile(VdexWrapper.getSchematicDirectory(server).toFile())
                        return@executes 1
                    }

                    it.source.sendFailure(Component.translatable("command.valkyrienskies.schematic.open_folder.server"))

                    return@executes 0
                })
        )
    }
}
