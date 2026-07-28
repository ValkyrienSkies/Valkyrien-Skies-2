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
import org.valkyrienskies.mod.common.render.BakedGeometrySerializer.BlockEntityEntry
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

        // Flatten every section's palette into one whole-ship map. Each PaletteEntry already
        // carries its wireKey: for non-BE blocks that's the plain canonical BlockState string;
        // for BE blocks it's canonicalKey(state)+"#"+nbtFingerprint, so two same-state tanks with
        // different NBT (different fluid/level) land in distinct palette entries instead of being
        // collapsed onto whichever tank was baked first. The BE-instance records carry the same
        // wireKey, so the consumer joins beInstances[i].wireKey -> palette[wireKey].
        val palette = linkedMapOf<String, List<BakedGeometry>>()
        val beInstances = mutableListOf<BlockEntityEntry>()
        for (section in bakedSections) {
            for (entry in section.palette) {
                palette.putIfAbsent(entry.wireKey, entry.geometry)
            }
            beInstances.addAll(section.beInstances)
        }

        val outDir = mc.gameDirectory.toPath().resolve("vsbake")
        try {
            Files.createDirectories(outDir)
            val outFile = outDir.resolve("$filename.vdexgeom")
            FileOutputStream(outFile.toFile()).use { fos ->
                BakedGeometrySerializer.write(fos, palette, beInstances)
            }
            player?.displayClientMessage(
                Component.literal(
                    "Baked ${palette.size} unique blockstates and ${beInstances.size} block entities to $outFile"
                ),
                false
            )
        } catch (e: Exception) {
            player?.displayClientMessage(Component.literal("Failed to write file: ${e.message}"), false)
            return 0
        }

        return 1
    }
}
