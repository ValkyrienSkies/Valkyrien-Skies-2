package org.valkyrienskies.mod.common.air_pockets

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.core.Direction
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import java.util.BitSet
import java.util.concurrent.CompletableFuture

internal data class ShipPocketState(
    var minX: Int = 0,
    var minY: Int = 0,
    var minZ: Int = 0,
    var sizeX: Int = 0,
    var sizeY: Int = 0,
    var sizeZ: Int = 0,
    var open: BitSet = BitSet(),
    var exterior: BitSet = BitSet(),
    // Boundary-connected open space that is *not* in the simulation domain.
    // Used to qualify real outside openings for inlets/vents, and to prevent "through-domain" false holes.
    var outsideVoid: BitSet = BitSet(),
    // Strict component-derived interior (no heuristic promotion).
    var strictInterior: BitSet = BitSet(),
    // Flood/suppression gameplay domain. Can include promoted leaky-pocket cells.
    var simulationDomain: BitSet = BitSet(),
    var interior: BitSet = BitSet(),
    var floodFluid: Fluid = Fluids.WATER,
    var flooded: BitSet = BitSet(),
    var materializedWater: BitSet = BitSet(),
    var brokenByFlood: BitSet = BitSet(),
    // Cells currently above an active drain plane. While set, shipyard fluid placement back into the cell
    // should be blocked so vanilla flow doesn't immediately undo drain progress.
    var drainSuppressed: BitSet = BitSet(),
    var waterReachable: BitSet = BitSet(),
    var unreachableVoid: BitSet = BitSet(),
    // Face conductance masks (shape-aware connectivity), stored on positive axes only.
    var faceCondXP: ShortArray = ShortArray(0),
    var faceCondYP: ShortArray = ShortArray(0),
    var faceCondZP: ShortArray = ShortArray(0),
    var shapeTemplatePalette: List<ShapeCellTemplate> = emptyList(),
    var templateIndexByVoxel: IntArray = IntArray(0),
    var voxelExteriorComponentMask: LongArray = LongArray(0),
    var voxelInteriorComponentMask: LongArray = LongArray(0),
    var voxelSimulationComponentMask: LongArray = LongArray(0),
    var componentGraphDegraded: Boolean = false,
    var buoyancy: BuoyancyMetrics = BuoyancyMetrics(),
    var floodPlaneByComponent: Int2DoubleOpenHashMap = Int2DoubleOpenHashMap(),
    var geometryRevision: Long = 0,
    var geometrySignature: Long = 0L,
    var geometryInvalidationStamp: Long = 0,
    var pendingGeometryFuture: CompletableFuture<GeometryAsyncResult>? = null,
    var requestedGeometryGeneration: Long = 0,
    var appliedGeometryGeneration: Long = 0,
    var geometryJobInFlight: Boolean = false,
    var geometryLastComputeNanos: Long = 0,
    var geometryComputeCount: Long = 0,
    var pendingWaterSolveFuture: CompletableFuture<WaterSolveResult>? = null,
    var requestedWaterSolveGeneration: Long = 0,
    var appliedWaterSolveGeneration: Long = 0,
    var lastWaterSolveSubmitTick: Long = Long.MIN_VALUE,
    var lastWaterSolveApplyTick: Long = Long.MIN_VALUE,
    var lastClientDemandTick: Long = Long.MIN_VALUE,
    var lastClientWaterSolveSubmittedTransformKey: Long = Long.MIN_VALUE,
    var lastClientWaterSolveAppliedTransformKey: Long = Long.MIN_VALUE,
    var lastClientWaterSolveApplyTick: Long = Long.MIN_VALUE,
    var consecutiveWaterSolveDiscards: Int = 0,
    var waterSolveJobInFlight: Boolean = false,
    var waterSolveLastComputeNanos: Long = 0,
    var waterSolveComputeCount: Long = 0,
    var requestedCullMaskGeneration: Long = 0,
    var appliedCullMaskGeneration: Long = 0,
    var queuedFloodAdds: BitSet = BitSet(),
    var queuedFloodRemoves: BitSet = BitSet(),
    var queuedFloodAddOrder: IntArrayList = IntArrayList(),
    var activeFloodIngressPoints: Int = 1,
    var activeFloodIngressConductanceUnits: Int = 1,
    var nextQueuedAddOrderIdx: Int = 0,
    var nextQueuedAddIdx: Int = 0,
    var nextQueuedRemoveIdx: Int = 0,
    var dirty: Boolean = true,
    var persistDirty: Boolean = true,
    var restoredFromPersistence: Boolean = false,
    var awaitingGeometryValidation: Boolean = false,
    var lastPersistFlushTick: Long = Long.MIN_VALUE,
    var lastFloodUpdateTick: Long = Long.MIN_VALUE,
    var lastWaterReachableUpdateTick: Long = Long.MIN_VALUE,
    var lastMaterializedResyncTick: Long = Long.MIN_VALUE,
    // Ship "gravity" for shipyard fluids is discrete (one of the 6 directions). When it changes due to ship
    // rotation, vanilla fluids won't tick automatically; schedule a budgeted wave of fluid ticks so they resettle.
    var lastGravityDownDir: Direction? = null,
    var pendingGravityResettleNextIdx: Int = -1,
)

internal data class BuoyancyMetrics(
    var submergedAirVolume: Double = 0.0,
    var submergedAirSumX: Double = 0.0,
    var submergedAirSumY: Double = 0.0,
    var submergedAirSumZ: Double = 0.0,
) {
    fun reset() {
        submergedAirVolume = 0.0
        submergedAirSumX = 0.0
        submergedAirSumY = 0.0
        submergedAirSumZ = 0.0
    }
}
