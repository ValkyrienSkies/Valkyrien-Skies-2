package org.valkyrienskies.mod.common.command.arguments

import com.google.gson.JsonObject
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.synchronization.ArgumentTypeInfo
import net.minecraft.network.FriendlyByteBuf
import org.valkyrienskies.mod.common.command.arguments.ShipArgument.Companion

internal class ShipArgumentInfo : ArgumentTypeInfo<ShipArgument, ContraptionArgumentInfoTemplate> {
    override fun serializeToNetwork(template: ContraptionArgumentInfoTemplate, friendlyByteBuf: FriendlyByteBuf) {
        friendlyByteBuf.writeBoolean(template.selectorOnly)
    }

    override fun deserializeFromNetwork(friendlyByteBuf: FriendlyByteBuf): ContraptionArgumentInfoTemplate {
        return ContraptionArgumentInfoTemplate(
            this, friendlyByteBuf.readBoolean()
        )
    }

    override fun unpack(argumentType: ShipArgument): ContraptionArgumentInfoTemplate {
        return ContraptionArgumentInfoTemplate(this, argumentType.selectorOnly)
    }

    override fun serializeToJson(template: ContraptionArgumentInfoTemplate, jsonObject: JsonObject) {
        jsonObject.addProperty("selectorOnly", template.selectorOnly)
    }
}

internal class ContraptionArgumentInfoTemplate(private val info: ShipArgumentInfo, internal val selectorOnly: Boolean) :
    ArgumentTypeInfo.Template<ShipArgument> {
    override fun instantiate(commandBuildContext: CommandBuildContext): ShipArgument {
        return if (selectorOnly) {
            ShipArgument.selectorOnly()
        } else {
            Companion.ships()
        }
    }

    override fun type(): ArgumentTypeInfo<ShipArgument, *> {
        return info
    }
}
