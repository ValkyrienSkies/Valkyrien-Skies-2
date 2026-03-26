package org.valkyrienskies.mod.common.command

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandRuntimeException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Component.translatable
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.core.internal.ShipTeleportData
import org.valkyrienskies.mod.common.command.commands.BackendCommand
import org.valkyrienskies.mod.common.command.commands.DeleteCommand
import org.valkyrienskies.mod.common.command.commands.DryCommand
import org.valkyrienskies.mod.common.command.commands.GetAirCommand
import org.valkyrienskies.mod.common.command.commands.GetGravityCommand
import org.valkyrienskies.mod.common.command.commands.GetShipCommand
import org.valkyrienskies.mod.common.command.commands.RemassCommand
import org.valkyrienskies.mod.common.command.commands.RenameCommand
import org.valkyrienskies.mod.common.command.commands.ScaleCommand
import org.valkyrienskies.mod.common.command.commands.SplittingCommand
import org.valkyrienskies.mod.common.command.commands.StaticCommand
import org.valkyrienskies.mod.common.command.commands.TeleportCommand
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.mixin.feature.commands.ClientSuggestionProviderAccessor
import org.valkyrienskies.mod.util.logger
import java.text.NumberFormat

object VSCommands {
    private val LOGGER by logger()

    fun registerServerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        var vs = literal("vs")

        BackendCommand.register(vs)
        DeleteCommand.register(vs)
        DryCommand.register(vs)
        GetAirCommand.register(vs)
        GetGravityCommand.register(vs)
        GetShipCommand.register(vs)
        RemassCommand.register(vs)
        RenameCommand.register(vs)
        ScaleCommand.register(vs)
        SplittingCommand.register(vs)
        StaticCommand.register(vs)
        TeleportCommand.register(vs)

        dispatcher.register(vs)
    }



    fun registerClientCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // TODO implement client commands
    }
}

fun ShipTeleportData.getMessage(): String {
    // "(${this.newDimension}) ${this.newPos}, rotation ${this.newRot}, velocity ${this.newVel}"
    return translatable(
        "command.valkyrienskies.teleport.teleport_data",
        this.newDimension?.toNiceString() ?: "",
        this.newPos.toNiceString(),
        this.newRot.toNiceString(),
        this.newVel.toNiceString()
    ).string
}

fun Vector3dc.toNiceString(): String {
    return (this as Vector3d).toString(NumberFormat.getInstance())
}

fun Quaterniondc.toNiceString(): String {
    return (this as Quaterniond).toString(NumberFormat.getInstance())
}

fun DimensionId.toNiceString(): String {
    val split = this.split(":")
    return split[2] + split[3]
}

val SharedSuggestionProvider.shipWorld: ShipWorld
    get() {
        return (
            if (this is CommandSourceStack) {
                return this.level.shipObjectWorld
            } else if (this is ClientSuggestionProviderAccessor) {
                checkNotNull(this.minecraft.level)
                return this.minecraft.level.shipObjectWorld
            } else {
                // Shouldn't happen
                throw CommandRuntimeException(Component.literal("Command source wasn't CommandSourceStack or ClientSuggestionProvider? Please report this as a bug"))
            }
            )
    }

