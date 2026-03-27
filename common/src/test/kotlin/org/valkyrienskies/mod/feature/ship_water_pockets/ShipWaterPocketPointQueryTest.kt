package org.valkyrienskies.mod.feature.ship_water_pockets

import io.mockk.mockk
import org.joml.Vector3d
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LadderBlock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.air_pockets.PointVoidClass
import org.valkyrienskies.mod.common.air_pockets.ShipPocketState
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager
import org.valkyrienskies.mod.common.air_pockets.buildShapeCellTemplate
import org.valkyrienskies.mod.common.air_pockets.classifyShipPointWithEpsilon
import org.valkyrienskies.mod.common.air_pockets.computeShapeWaterGeometry
import org.valkyrienskies.mod.common.air_pockets.findNearbyAirPocket
import org.valkyrienskies.mod.common.air_pockets.findNearbyWorldFluidSuppressionZone
import org.valkyrienskies.mod.common.air_pockets.fullComponentMask

class ShipWaterPocketPointQueryTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun walkThroughSolidSubshapeStillFindsAirPocketAndSuppressionCell() {
        val level = mockk<Level>(relaxed = true)
        val pos = BlockPos(0, 64, 0)
        val state = Blocks.LADDER.defaultBlockState()
            .setValue(LadderBlock.FACING, Direction.NORTH)

        val geometry = computeShapeWaterGeometry(level, pos, state)
        val ladderBox = geometry.boxes.first()
        val pointX = ladderBox.center.x
        val pointY = ladderBox.center.y
        val pointZ = ladderBox.center.z

        val template = buildShapeCellTemplate(geometry)
        val componentMask = fullComponentMask(template.componentCount)
        val pocketState = ShipPocketState(
            sizeX = 1,
            sizeY = 1,
            sizeZ = 1,
            open = bitSetOf(0),
            strictInterior = bitSetOf(0),
            simulationDomain = bitSetOf(0),
            interior = bitSetOf(0),
            unreachableVoid = bitSetOf(0),
            shapeTemplatePalette = listOf(template),
            templateIndexByVoxel = intArrayOf(0),
            voxelInteriorComponentMask = longArrayOf(componentMask),
            voxelSimulationComponentMask = longArrayOf(componentMask),
            voxelExteriorComponentMask = longArrayOf(0L),
        )

        val shipPoint = Vector3d(pointX, pointY, pointZ)
        val tmp = BlockPos.MutableBlockPos(0, 0, 0)

        val classification = classifyShipPointWithEpsilon(pocketState, pointX, pointY, pointZ, tmp)
        assertEquals(PointVoidClass.SOLID, classification.kind)

        tmp.set(0, 0, 0)
        assertNotNull(findNearbyWorldFluidSuppressionZone(pocketState, shipPoint, tmp, 0))

        tmp.set(0, 0, 0)
        assertNotNull(findNearbyAirPocket(pocketState, shipPoint, tmp, 0))
    }

    @Test
    fun currentShipFastPathTreatsSolidSubshapeAsAirPocket() {
        val level = mockk<Level>(relaxed = true)
        val pos = BlockPos(0, 64, 0)
        val state = Blocks.LADDER.defaultBlockState()
            .setValue(LadderBlock.FACING, Direction.NORTH)

        val geometry = computeShapeWaterGeometry(level, pos, state)
        val ladderBox = geometry.boxes.first()
        val pointX = ladderBox.center.x
        val pointY = ladderBox.center.y
        val pointZ = ladderBox.center.z

        val template = buildShapeCellTemplate(geometry)
        val componentMask = fullComponentMask(template.componentCount)
        val pocketState = ShipPocketState(
            sizeX = 1,
            sizeY = 1,
            sizeZ = 1,
            open = bitSetOf(0),
            strictInterior = bitSetOf(0),
            simulationDomain = bitSetOf(0),
            interior = bitSetOf(0),
            unreachableVoid = bitSetOf(0),
            shapeTemplatePalette = listOf(template),
            templateIndexByVoxel = intArrayOf(0),
            voxelInteriorComponentMask = longArrayOf(componentMask),
            voxelSimulationComponentMask = longArrayOf(componentMask),
            voxelExteriorComponentMask = longArrayOf(0L),
        )

        val method = ShipWaterPocketManager::class.java.getDeclaredMethod(
            "isCurrentShipSampleInAirPocket",
            ShipPocketState::class.java,
            java.lang.Double.TYPE,
            java.lang.Double.TYPE,
            java.lang.Double.TYPE,
            Vector3d::class.java,
            BlockPos.MutableBlockPos::class.java,
        )
        method.isAccessible = true

        val result = method.invoke(
            ShipWaterPocketManager,
            pocketState,
            pointX,
            pointY,
            pointZ,
            Vector3d(),
            BlockPos.MutableBlockPos(),
        ) as Boolean

        assertTrue(result)
    }
}
