package org.valkyrienskies.mod.common.config

import com.github.imifou.jsonschema.module.addon.annotation.JsonSchema

object VSGameConfig {

    @JvmField
    val CLIENT = Client()

    @JvmField
    val SERVER = Server()

    class Client {
        @JsonSchema(description = "Renders the VS2 debug HUD with TPS")
        var renderDebugText = true
    }

    class Server {
        @JsonSchema(
            description = "By default, the vanilla server prevents block interacts past a certain distance " +
                "to prevent cheat clients from breaking blocks halfway across the map. This approach breaks down in the " +
                "face of extremely large ships, where the distance from the block origin to the nearest face is greater " +
                "than the interact distance check allows."
        )
        var enableInteractDistanceChecks = true

        @JsonSchema(
            description = "If true, teleportation into the shipyard is redirected to the ship it belongs to " +
                "instead."
        )
        var transformTeleports = true
    }
}
