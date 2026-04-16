package org.valkyrienskies.mod.common.command

import net.minecraft.commands.SharedSuggestionProvider
import org.valkyrienskies.mod.common.command.arguments.ShipArgumentParser

@Deprecated(
    replaceWith = ReplaceWith("ShipArgumentParser", "org.valkyrienskies.mod.common.command.arguments.ShipArgumentParser"),
    message = "Moved to arguments package",
    level = DeprecationLevel.ERROR
)
class ShipArgumentParser(source: SharedSuggestionProvider?, selectorOnly: Boolean): ShipArgumentParser(source, selectorOnly)
