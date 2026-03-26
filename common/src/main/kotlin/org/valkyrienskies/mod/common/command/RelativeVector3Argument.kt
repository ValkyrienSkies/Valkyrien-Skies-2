package org.valkyrienskies.mod.common.command

import org.valkyrienskies.mod.common.command.arguments.RelativeVector3Argument

@Deprecated(
    replaceWith = ReplaceWith("RelativeVector3Argument", "org.valkyrienskies.mod.common.command.arguments.RelativeVector3Argument"),
    message = "Moved to arguments package",
    level = DeprecationLevel.ERROR
)
class RelativeVector3Argument: RelativeVector3Argument()
