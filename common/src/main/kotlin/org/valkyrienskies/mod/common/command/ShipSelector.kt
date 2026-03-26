package org.valkyrienskies.mod.common.command

import org.valkyrienskies.mod.common.command.arguments.ShipSelector

@Deprecated(
    replaceWith = ReplaceWith("ShipSelector", "org.valkyrienskies.mod.common.command.arguments.ShipSelector"),
    message = "Moved to arguments package",
    level = DeprecationLevel.ERROR
)
typealias ShipSelector = ShipSelector
