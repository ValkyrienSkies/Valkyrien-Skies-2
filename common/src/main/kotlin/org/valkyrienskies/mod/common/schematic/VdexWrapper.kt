package org.valkyrienskies.mod.common.schematic

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.world.level.storage.LevelResource
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3i
import org.valkyrienskies.core.api.attachment.AttachmentHolder
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.internal.joints.VSD6Joint
import org.valkyrienskies.core.internal.joints.VSDistanceJoint
import org.valkyrienskies.core.internal.joints.VSFixedJoint
import org.valkyrienskies.core.internal.joints.VSGearJoint
import org.valkyrienskies.core.internal.joints.VSJointPose
import org.valkyrienskies.core.internal.joints.VSPrismaticJoint
import org.valkyrienskies.core.internal.joints.VSRackAndPinionJoint
import org.valkyrienskies.core.internal.joints.VSRevoluteJoint
import org.valkyrienskies.core.internal.joints.VSSphericalJoint
import org.valkyrienskies.core.internal.joints.VSSpringJoint
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.assembly.ICopyableAttachment
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.assembly.ShipAssembler.SingleItemMap
import org.valkyrienskies.mod.common.command.commands.SchematicCommand.shipsNeedingAttachmentLoad
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.safeRenameTo
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.floorToInt
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.common.yRange
import org.valkyrienskies.mod.util.StructureTemplateFillFromVoxelSet
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

object VdexWrapper {
    fun getSchematicDirectory(server: MinecraftServer): Path {
        val worldDir = server.getWorldPath(LevelResource.ROOT)
        val schematicsDir = worldDir.resolve("schematics")
        Files.createDirectories(schematicsDir)
        return schematicsDir
    }

    /**
     * Returned component is null if the loading was successful, otherwise it will be the error message (translatable)
     */
    fun loadShip(level: ServerLevel, position: Vector3d, filename: String): Component? {
        val schematicsDir = getSchematicDirectory(level.server)

        val schem: VdexData = try {
            val filePath = schematicsDir.resolve("$filename.vdex")
            VdexIO.read(filePath)
        } catch (e: FileNotFoundException) {
            return Component.translatable("command.valkyrienskies.schematic.load.fail.no_such_file", filename)
        } catch (e: InvalidPathException) {
            // I don't think this exception is actually possible,
            // since brigadier already checks for special characters
            // But I'll keep it just in case
            return Component.translatable("command.valkyrienskies.schematic.load.fail.invalid_path", filename)
        } catch (e: IllegalStateException) {
            return Component.translatable("command.valkyrienskies.schematic.load.fail.not_vdex", filename)
        }

        // Vector3dc because we don't want to mutate this as we go along
        val pastePos: Vector3dc = position
        val pasteRot = Quaterniond() // identity for now, todo: make rotation command parameter

        val indexToShip = mutableMapOf<Int, ServerShip>()
        schem.metadata.ships.forEachIndexed { index, vdexShipEntry ->
            val worldPos = Vector3d(vdexShipEntry.relativePos)
                .rotate(pasteRot)
                .add(pastePos)

            val worldRot = Quaterniond(pasteRot)
                .mul(Quaterniond(vdexShipEntry.relativeRot))

            val ship = level.shipObjectWorld.createNewShipAtBlock(
                worldPos.floorToInt(),
                false,
                vdexShipEntry.scale,
                level.dimensionId
            )
            indexToShip[index] = ship

            ship.isStatic = vdexShipEntry.isStatic
            ship.safeRenameTo(level, vdexShipEntry.name)
            ship.unsafeSetTransform(
                vsCore.newBodyTransform(
                    worldPos,
                    worldRot,
                    Vector3d(vdexShipEntry.scale),
                    ship.transform.positionInModel
                )
            )

            shipsNeedingAttachmentLoad.put(ship.id, vdexShipEntry.attachedData)

            val structureTag = schem.nbtData[vdexShipEntry.nbtFile]
                ?: return Component.translatable("command.valkyrienskies.schematic.load.fail.missing_structure", vdexShipEntry.nbtFile)

            val template = StructureTemplate()
            template.load(level.holderLookup(Registries.BLOCK), structureTag)

            // Place blocks in the ship's chunk claim
            val toCenter = ship.chunkClaim.getCenterBlockCoordinates(level.yRange, Vector3i())

            // Calculate corner position so the structure is centered in the claim
            val halfSize = Vector3d(template.size.x / 2.0, template.size.y / 2.0, template.size.z / 2.0)
            val cornerOfShip = BlockPos(
                toCenter.x - halfSize.x.toInt(),
                toCenter.y - halfSize.y.toInt(),
                toCenter.z - halfSize.z.toInt()
            )

            val settings = StructurePlaceSettings()
            settings.rotationPivot = cornerOfShip
            template.placeInWorld(level, cornerOfShip, cornerOfShip, settings, level.random, Block.UPDATE_CLIENTS)
        }

        val gtpa = ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)
        schem.metadata.constraints.forEach { pair ->
            val joint = pair.joint
            val meta = pair.metadata

            val ship0 = indexToShip[meta.shipIndex0] ?: return@forEach
            val ship1 = indexToShip[meta.shipIndex1] ?: return@forEach

            val newPos0 = ship0.shipAABB?.center(Vector3d())?.add(meta.position0offset, Vector3d()) ?: return@forEach
            val newRot0 = joint.pose0.rot

            val newPos1 = ship1.shipAABB?.center(Vector3d())?.add(meta.position1offset, Vector3d()) ?: return@forEach
            val newRot1 = joint.pose1.rot

            val newId0 = ship0.id
            val newId1 = ship1.id

            // Absolutely hideous
            val newJoint = when (joint) {
                is VSFixedJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSDistanceJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSSpringJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSPrismaticJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSSphericalJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSRevoluteJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSGearJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSRackAndPinionJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSD6Joint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                else -> {
                    return Component.translatable("command.valkyrienskies.schematic.load.fail.bad_joint_type", joint::class.simpleName)
                }
            }

            gtpa.addJoint(newJoint, 2) {}
        }

        return null
    }

    fun saveShip(level: ServerLevel, mainShip: ServerShip, filename: String, creator: String = "Unknown", description: String = "No description provided."): VdexMetadata {

        //todo: add stylistic name param to command probably
        val name = filename

        // Find all connected ships via constraints
        val gtpa = ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)
        val connectedIds = gtpa.getAllConnectedShips(mainShip.id)
        val allShips = connectedIds.mapNotNull { id ->
            level.shipObjectWorld.allShips.getById(id)
        }
        val collectedModList = mutableListOf<String>()

        // Main ship is index 0; find its index in the connected set
        val mainShipIndex = allShips.indexOfFirst { it.id == mainShip.id }.coerceAtLeast(0)

        // Reorder so main ship is first
        val orderedShips = mutableListOf<ServerShip>()
        orderedShips.add(allShips[mainShipIndex])
        for (i in allShips.indices) {
            if (i != mainShipIndex) orderedShips.add(allShips[i])
        }

        val mainTransform = orderedShips[0].transform
        val mainPos = mainTransform.position
        val mainRot = mainTransform.rotation
        val invMainRot = Quaterniond(mainRot).invert()

        val shipEntries = mutableListOf<VdexShipEntry>()
        val nbtData = mutableMapOf<String, CompoundTag>()

        for (idx in orderedShips.indices) {
            val ship = orderedShips[idx]
            val nbtFileName = "ship_$idx.nbt"
            val blocks = collectShipBlocks(level, ship, collectedModList)
            if (blocks.isEmpty()) continue

            val minMax = ShipAssembler.findMinAndMax(blocks)
            val minB = minMax.first
            val maxB = minMax.second
            val template = StructureTemplate()
            (template as StructureTemplateFillFromVoxelSet).`vs$fillFromVoxelSet`(
                level, blocks, listOf(ship),
                SingleItemMap(ship.id, Vector3d(), Vector3d()),
                minB, maxB
            )

            val tag = template.save(CompoundTag())
            nbtData[nbtFileName] = tag

            val relPos = Vector3d(ship.transform.position)
                .sub(mainPos)
                .rotate(invMainRot)

            val relRot = Quaterniond(invMainRot)
                .mul(Quaterniond(ship.transform.rotation))

            val attachmentData = CompoundTag()
            if (ship is AttachmentHolder) {
                val attachments = (ship as AttachmentHolder).getAllAttachments()
                for (attachment in attachments) {
                    if (attachment is ICopyableAttachment) {
                        attachmentData.put(attachment.javaClass.name, attachment.saveToTag())
                    }
                }
            }

            val baos = ByteArrayOutputStream()
            NbtIo.write(attachmentData, DataOutputStream(baos))

            val attachmentsAsBytes = baos.toByteArray()

            shipEntries.add(VdexShipEntry(
                name = ship.slug ?: "ship_$idx",
                nbtFile = nbtFileName,
                relativePos = relPos,
                relativeRot = relRot,
                isStatic = ship.isStatic,
                scale = ship.transform.scaling.x(),
                attachedData = attachmentsAsBytes
            ))
        }

        // Build ship ID -> index map for constraint references
        val shipIdToIndex = mutableMapOf<Long, Int>()
        for (idx in orderedShips.indices) {
            shipIdToIndex[orderedShips[idx].id] = idx
        }

        // Collect constraints between ships in the set
        val constraintEntries = mutableListOf<VdexConstraintEntry>()
        val seenJoints = mutableSetOf<Any>()
        for (ship in orderedShips) {
            val jointIds = gtpa.getJointsFromShip(ship.id) ?: continue
            for (jointId in jointIds) {
                //println("test")
                if (!seenJoints.add(jointId)) continue
                val joint = gtpa.getJointById(jointId) ?: continue
                val idx0 = shipIdToIndex[joint.shipId0] ?: continue
                val idx1 = shipIdToIndex[joint.shipId1] ?: continue

                // We can't rely on our ship iterator here because it could be either ship0 OR ship1
                val firstShip = level.shipObjectWorld.allShips.getById(joint.shipId0!!)!!
                val secondShip = level.shipObjectWorld.allShips.getById(joint.shipId1!!)!!

                constraintEntries.add(VdexConstraintEntry(
                    joint,
                    VdexConstraintMetadata(
                        idx0,
                        idx1,
                        joint.pose0.pos.sub(firstShip.shipAABB!!.center(Vector3d()), Vector3d()),
                        joint.pose1.pos.sub(secondShip.shipAABB!!.center(Vector3d()), Vector3d()),
                    )
                ))
            }
        }

        val socialMetadata = VdexSocialMetadata(
            name = name,
            author = creator,
            description = description
        )

        val metadata = VdexMetadata(
            version = 1,
            social = socialMetadata,
            ships = shipEntries,
            constraints = constraintEntries,
            shipIdToIndex = shipIdToIndex
        )

        //todo, figure out some way to get real mod versions
        val modList = mutableListOf(
            VdexModEntry("valkyrienskies", ValkyrienSkiesMod.platformUtils.getModVersion("valkyrienskies"), true),
        )
        modList.addAll(
            collectedModList
                .distinct()
                .sorted()
                .map { VdexModEntry(it, ValkyrienSkiesMod.platformUtils.getModVersion(it), true) }
        )

        // Save to world/schematics/ directory
        val schematicsDir = getSchematicDirectory(level.server)

        val filePath = schematicsDir.resolve("$filename.vdex")

        VdexIO.write(filePath, metadata, modList, nbtData)


        return metadata.copy()
    }

    fun collectShipBlocks(level: ServerLevel, ship: ServerShip, collectedModList: MutableList<String> = mutableListOf()): List<BlockPos> {
        val blocks = mutableListOf<BlockPos>()
        //if (ship !is LoadedServerShip) return blocks

        ship.activeChunksSet.forEach { cx, cz ->
            val chunk = level.getChunk(cx, cz)
            val sections = chunk.sections
            for (sIdx in sections.indices) {
                val section = sections[sIdx] ?: continue
                if (section.hasOnlyAir()) continue
                val baseY = chunk.getSectionYFromSectionIndex(sIdx) shl 4
                for (lx in 0..15) {
                    for (ly in 0..15) {
                        for (lz in 0..15) {
                            val state = section.getBlockState(lx, ly, lz)
                            if (!state.isAir) {
                                val namespace = BuiltInRegistries.BLOCK.getKey(state.block).namespace
                                if (!collectedModList.contains(namespace) && namespace != "valkyrienskies") {
                                    collectedModList.add(namespace)
                                }
                                blocks.add(BlockPos(
                                    (cx shl 4) + lx,
                                    baseY + ly,
                                    (cz shl 4) + lz
                                ))
                            }
                        }
                    }
                }
            }
        }
        return blocks
    }
}
