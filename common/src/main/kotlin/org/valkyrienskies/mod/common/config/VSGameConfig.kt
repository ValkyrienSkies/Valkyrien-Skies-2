package org.valkyrienskies.mod.common.config

import com.github.imifou.jsonschema.module.addon.annotation.JsonSchema

object VSGameConfig {

    @JvmField
    val CLIENT = Client()

    @JvmField
    val SERVER = Server()

    @JvmField
    val COMMON = Common()

    class Client {
        @JsonSchema(description = "Renders the VS2 debug HUD with TPS")
        var renderDebugText = false
    }

    class Server {
        @JsonSchema(
            description = "By default, the vanilla server prevents block interacts past a certain distance " +
                "to prevent cheat clients from breaking blocks halfway across the map. " +
                "This approach breaks down in the face of extremely large ships, " +
                "where the distance from the block origin to the nearest face is greater " +
                "than the interact distance check allows."
        )
        var enableInteractDistanceChecks = true

        @JsonSchema(
            description = "If true, teleportation into the shipyard is redirected to " +
                "the ship it belongs to instead."
        )
        var transformTeleports = true

        @JsonSchema(
            description = "By default, the server checks that player movement is legal, and if it isn't, " +
                "rubber-bands the player with the infamous \"moved too quickly\" message. Since players on VS ships " +
                "will move illegally, they will be affected by this check frequently. This option disables that " +
                "check. (it doesn't work very well anyway, don't worry)"
        )
        var enableMovementChecks = false

        @JsonSchema(
            description = "If true, prevents water and other fluids from flowing out of the ship's bounding box."
        )
        var preventFluidEscapingShip = true

        @JsonSchema(
            description = "Blast force in newtons of a TNT explosion at the center of the explosion."
        )
        var explosionBlastForce = 500000.0

        @JsonSchema(
            description = "Allow natural mob spawning on ships"
        )
        var allowMobSpawns = true

        @JsonSchema(
            description = "Allow rudimentary pathfinding on ships"
        )
        var aiOnShips = true
    }

    class Common {

        @JvmField
        @JsonSchema(title = "Advanced")
        val ADVANCED = Advanced()

        class Advanced { // Debug configs that may be either side
            @JsonSchema(
                description = "Renders mob pathfinding nodes. Must be set on client and server to work. " +
                    "Requires the system property -Dorg.valkyrienskies.render_pathfinding=true"
            )
            var renderPathfinding = true // Requires ValkyrienCommonMixinConfigPlugin.PATH_FINDING_DEBUG to be true
        }
    }
}
