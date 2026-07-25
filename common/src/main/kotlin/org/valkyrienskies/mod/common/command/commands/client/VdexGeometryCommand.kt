package org.valkyrienskies.mod.common.command.commands.client

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.common.render.BakedGeometrySerializer
import org.valkyrienskies.mod.common.render.BakedGeometrySerializer.BlockStateKeys
import org.valkyrienskies.mod.common.render.Bakery.BakedGeometry
import org.valkyrienskies.mod.common.render.SectionBakery
import org.valkyrienskies.mod.common.shipObjectWorld
import java.io.FileOutputStream
import java.nio.file.Files

object VdexGeometryCommand {


    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(
            literal("vdexbake")
                .then(
                    // bricky fix this, this is your fault not mine blehh
                    argument("ship-slug", StringArgumentType.word())
                    .then(
                        argument("filename", StringArgumentType.word())
                            .executes { ctx ->
                                val filename = StringArgumentType.getString(ctx, "filename")
                                val shipSlug = StringArgumentType.getString(ctx, "ship-slug")
                                // find the ship by slug
                                val ship = Minecraft.getInstance().level.shipObjectWorld.loadedShips.find { it.slug == shipSlug }
                                if (ship == null) {
                                    ctx.source.sendFailure(Component.literal("Ship with slug '$shipSlug' not found."))
                                    return@executes 0
                                }

                                bakeShip(ship, filename)
                            }
                    )
                )
        )
    }

    private fun bakeShip(ship: ClientShip, filename: String): Int {
        val mc = Minecraft.getInstance()
        val player = mc.player

        val bakery = SectionBakery()
        val bakedSections = bakery.ingestShip(ship)

        if (bakedSections.isEmpty()) {
            player?.displayClientMessage(Component.literal("Ship has no non-air sections to bake."), false)
            return 0
        }

        // Flatten every section's palette into one whole-ship map, keyed by canonical
        // BlockState string — matches the "no position duplication, website already has
        // NBT" format from before, rather than writing sections separately.
        val palette = linkedMapOf<String, List<BakedGeometry>>()
        for (section in bakedSections) {
            for (entry in section.palette) {
                val key = BlockStateKeys.canonicalKey(entry.state)
                palette.putIfAbsent(key, entry.geometry)
            }
        }

        val outDir = mc.gameDirectory.toPath().resolve("vsbake")
        try {
            Files.createDirectories(outDir)
            val outFile = outDir.resolve("$filename.vdexgeom")
            FileOutputStream(outFile.toFile()).use { fos ->
                BakedGeometrySerializer.write(fos, palette)
            }
            player?.displayClientMessage(
                Component.literal("Baked ${palette.size} unique blockstates to $outFile"),
                false
            )
        } catch (e: Exception) {
            player?.displayClientMessage(Component.literal("Failed to write file: ${e.message}"), false)
            return 0
        }

        return 1
    }
}
