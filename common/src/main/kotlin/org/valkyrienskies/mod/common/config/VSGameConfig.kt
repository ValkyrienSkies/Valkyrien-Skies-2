package org.valkyrienskies.mod.common.config

import org.valkyrienskies.core.internal.config.ConfigCategory
import org.valkyrienskies.core.internal.config.ConfigEntry
import org.valkyrienskies.mod.mixinducks.feature.mass_tooltip.MassTooltipVisibility

object VSGameConfig {

    @JvmField
    val CLIENT = Client()

    @JvmField
    val SERVER = Server()

    @JvmField
    val COMMON = Common()

    class Client {
        @ConfigCategory(title = "Tooltips")
        val Tooltip = TOOLTIP()

        @ConfigCategory(title = "Block Tint")
        val BlockTinting = BLOCKTINT()

        @ConfigCategory(title = "SoundEffect")
        val SoundEffect = SOUNDEFFECT()
        
        @ConfigCategory(title = "Connectivity")
        val Connectivity = CONNECTIVITY()

        @ConfigEntry(description = "Renders the VS2 debug HUD with TPS")
        var renderDebugText = false

        @ConfigEntry(description = "Grace period before a player's camera is no longer considered to be within a sealed area")
        var sealedAreaCameraGracePeriod = 2

        @ConfigEntry(
            description = "Recommend ship slugs in mc commands where player names could be used ex. /tp ship-name wich could pollute user autocomplete"
        )
        var recommendSlugsInMcCommands = true

        class TOOLTIP {
            @ConfigEntry(
                description = "Set when the Mass Tooltip is Visible"
            )
            var massTooltipVisibility = MassTooltipVisibility.ADVANCED

            @ConfigEntry(
                description = "Use Imperial Units to show Mass"
            )
            var useImperialUnits = false
        }

        class BLOCKTINT {
            @ConfigEntry(
                description = "Partly fixes the block tinting issue with blocks on ships"
            )
            var fixBlockTinting = false
        }

        class SOUNDEFFECT {
            @ConfigEntry(description = "Enable doppler effect for sounds")
            var enableDopplerEffect = true

            @ConfigEntry(description = "Doppler effect scaler. 1.0 for realistic; smaller value results in weaker doppler effect")
            var dopplerEffectScale = 1.0 / 20
        }
        class CONNECTIVITY {
            @ConfigEntry(
                description = "Enable client connectivity; increases client load, but allows for client-sided sealed space visuals, like occluding water in a submarine."
            )
            var enableClientConnectivity = false
        }

        @ConfigEntry(
            description = "The way ships are rendered by default"
        )
        var defaultRenderer = ShipRenderer.VANILLA
    }

    class Server {
        @ConfigCategory(title = "FTB Chunks")
        val FTBChunks = FTBCHUNKS()

        class FTBCHUNKS {
            @ConfigEntry(
                description = "Are Ships protected by FTB Chunk Claims?"
            )
            var shipsProtectedByClaims = true

            @ConfigEntry(
                description = "Are ships protected outside of build height (max and min)?"
            )
            var shipsProtectionOutOfBuildHeight = false
        }

        @ConfigCategory(title = "ComputerCraft")
        val ComputerCraft = COMPUTERCRAFT()

        class COMPUTERCRAFT {
            @ConfigEntry(
                description = "Turtles leaving scaled up/down ship may cause issues" +
                    "Enable/Disable Turtles Leaving Scaled Ships?"
            )
            var canTurtlesLeaveScaledShips = false
        }

        @ConfigCategory(title = "Weather 2")
        val Weather2 = WEATHER2()

        class WEATHER2 {
            @ConfigEntry(
                description = "If VS ships are affected by Weather2"
            )
            var enableWeatherCompat = true

            @ConfigEntry(
                description = "How much Weather 2's wind affects VS ships"
            )
            var windMultiplier = 0.1f

            @ConfigEntry(
                description = "The maximum velocity a VS ship can travel because of wind"
            )
            var windMaxVel = 20.0f

            @ConfigEntry(
                description = "In what range storms affect VS ships"
            )
            var stormRange = 150.0

            @ConfigEntry(
                description = "Storm effect dampening on VS ships"
            )
            var stormDampening = 0.0f
        }

        @ConfigCategory(title = "Dynmap")
        val Dynmap = DYNMAP()

        class DYNMAP {
            @ConfigEntry(description = "Show Ships as Icon Markers on Dynmap")
            var showIconMarkers = true
            @ConfigEntry(description = "Show Ships as Polyline Markers on Dynmap")
            var showPolylineMarkers = true
            @ConfigEntry(description = "Show the Ship ID in the label")
            var showShipId = true
            @ConfigEntry(description = "Show the Ship Mass in the label")
            var showShipMass = true
        }

        @ConfigCategory(title = "CBC")
        val Cbc = CBC()

        class CBC {
            @ConfigEntry(description = "Should cannon shots apply a recoil force to ships")
            var shellRecoil = false
            @ConfigEntry(description = "The force multiplier applied to recoil on ships")
            var shellRecoilMult = 500000.0
        }


        @ConfigEntry(
            description = "By default, the vanilla server prevents block interacts past a certain distance " +
                "to prevent cheat clients from breaking blocks halfway across the map. " +
                "This approach breaks down in the face of extremely large ships, " +
                "where the distance from the block origin to the nearest face is greater " +
                "than the interact distance check allows."
        )
        var enableInteractDistanceChecks = true

        @ConfigEntry(description = "If true, enables buoyancy from serverside air pockets.")
        var enablePocketBuoyancy = false

        @ConfigEntry(description = "Buoyancy factor added per cubic meter of air pocket inside a ship")
        var buoyancyFactorPerPocketVolume = 0.05 // per cubic meter

        @ConfigEntry(
            description = "If true, teleportation into the shipyard is redirected to " +
                "the ship it belongs to instead."
        )
        var transformTeleports = true

        @ConfigEntry(
            description = "By default, the server checks that player movement is legal, and if it isn't, " +
                "rubber-bands the player with the infamous \"moved too quickly\" message. Since players on VS ships " +
                "will move illegally, they will be affected by this check frequently. This option disables that " +
                "check. (it doesn't work very well anyway, don't worry)"
        )
        var enableMovementChecks = false

        @ConfigEntry(
            description = "If true, when a player disconnects, their position on the ship is saved such that " +
                "if the ship is moved, when they reconnect they will be teleported to the same position in the ship " +
                "as they left, instead of being left behind."
        )
        var teleportReconnectedPlayers = true

        @ConfigEntry(
            description = "If true, when a mob gets unloaded, its position on a ship is saved such that " +
                "if the ship is moved, when the mob loads back in it will be teleported to the same position in the ship." +
                " This helps prevent mobs from falling off of ships."
        )
        var saveMobsPositionOnShip = true

        @ConfigEntry(
            description = "If true, prevents water and other fluids from flowing out of the ship's bounding box."
        )
        var preventFluidEscapingShip = true

        @ConfigEntry(
            description = "If true, prevents vines from growing beyond the ship's bounding box."
        )
        var preventVinesEscapingShip = true

        @ConfigEntry(
            description = "Blast force in newtons of a TNT explosion at the center of the explosion."
        )
        var explosionBlastForce = 500000.0

        @ConfigEntry(
            description = "Allow natural mob spawning on ships"
        )
        var allowMobSpawns = true

        @ConfigEntry(
            description = "Allow rudimentary pathfinding on ships"
        )
        var aiOnShips = true

        @ConfigEntry(
            description = "Scale of the mini ship creator"
        )
        var miniShipSize = 0.5

        @ConfigEntry(
            description = "Minimum scale of ships"
        )
        var minScaling = 0.25

        @ConfigEntry(
            description = "Default mass for blocks that do not have it defined in data or code. Blocks with masses below 100 float in water"
        )
        var defaultBlockMass = 1000.0

        @ConfigEntry(
            description = "Default elasticity coefficient for blocks. Higher values make blocks more bouncy"
        )
        var defaultBlockElasticity = 0.3

        @ConfigEntry(
            description = "Default friction coefficient for blocks. Lower values make blocks more slippery"
        )
        var defaultBlockFriction = 0.5

        @ConfigEntry(
            description = "Default block hardness (unused value, placeholder for later)"
        )
        var defaultBlockHardness = 1.0

        @ConfigEntry(
            description = "Enable splitting in worldspace. (Experimental!)"
        )
        var enableWorldSplitting = false

        @ConfigEntry(
            description = "The default grace timer for splitting. A split won't occur after a block break at a position until this many ticks have passed. Note that setting this too high may prevent things like explosions from properly launching split ships. (in ticks)"
        )
        var defaultSplitGraceTimer = 2

        @ConfigCategory(title = "Commands")
        val Commands = COMMANDS()

        class COMMANDS {
            @ConfigEntry(
                description = "The permission level required to use the /vs delete command. Must be 0 <= x <= 4"
            )
            var deleteShipCommandPerms = 2

            @ConfigEntry(
                description = "The permission level required to use the /vs get-ship command. Must be 0 <= x <= 4"
            )
            var getShipCommandPerms = 0

            @ConfigEntry(
                description = "The permission level required to use the /vs get-air and /vs get-gravity command. Must be 0 <= x <= 4"
            )
            var getAirValuesPerms = 0

            @ConfigEntry(
                description = "The permission level required to use the /vs rename command. Must be 0 <= x <= 4"
            )
            var renameShipCommandPerms = 2

            @ConfigEntry(
                description = "The permission level required to use the /vs remass command. Must be 0 <= x <= 4"
            )
            var remassShipCommandPerms = 2

            @ConfigEntry(
                description = "The permission level required to use the /vs scale command. Must be 0 <= x <= 4"
            )
            var scaleShipCommandPerms = 2

            @ConfigEntry(
                description = "The permission level required to use the /vs set-static command. Must be 0 <= x <= 4"
            )
            var setStaticShipCommandPerms = 2

            @ConfigEntry(
                description = "The permission level required to use the /vs teleport command. Must be 0 <= x <= 4"
            )
            var teleportShipCommandPerms = 2

            @ConfigEntry(
                description = "The permission level required to use the /vs backend command. Must be 0 <= x <= 4"
            )
            var changeBackendCommandPerms = 4

            @ConfigEntry(
                description = "The permission level required to use the /vs dry command. Must be 0 <= x <= 4"
            )
            var dryShipCommandPerms = 2
        }
    }

    class Common {

        @JvmField
        @ConfigCategory(title = "Advanced")
        val ADVANCED = Advanced()

        class Advanced { // Debug configs that may be either side
            @ConfigEntry(
                description = "Renders mob pathfinding nodes. Must be set on client and server to work. " +
                    "Requires the system property -Dorg.valkyrienskies.render_pathfinding=true"
            )
            var renderPathfinding = false // Requires ValkyrienCommonMixinConfigPlugin.PATH_FINDING_DEBUG to be true
        }
    }
}
