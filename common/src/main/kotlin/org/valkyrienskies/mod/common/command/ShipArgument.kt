package org.valkyrienskies.mod.common.command

import org.valkyrienskies.mod.common.command.arguments.ShipArgument

@Deprecated(
    replaceWith = ReplaceWith("ShipArgument", "org.valkyrienskies.mod.common.command.arguments.ShipArgument"),
    message = "Moved to arguments package",
    level = DeprecationLevel.ERROR
)
typealias ShipArgument = ShipArgument
