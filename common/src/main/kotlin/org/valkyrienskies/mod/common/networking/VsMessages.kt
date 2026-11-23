package org.valkyrienskies.mod.common.networking

import net.minecraft.world.InteractionHand
import org.joml.Vector2i
import org.joml.Vector3f
import org.valkyrienskies.core.api.codec.VsCodec
import org.valkyrienskies.core.api.VsCoreApi
import org.valkyrienskies.core.api.codec.VsReader
import org.valkyrienskies.core.api.codec.VsWriter
import org.valkyrienskies.core.api.network.VsNetworkDirection
import org.valkyrienskies.core.api.network.VsNetworkMessageType
import org.valkyrienskies.core.internal.VsiCore
import org.valkyrienskies.core.internal.network.VsiNetworkMessageType
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.valkyrienskies.core.api.newEnumCodec
import org.valkyrienskies.mod.client.ClientBlockInfo
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import java.util.UUID

/**
 * VS2's network messages: compact positional codecs, explicit delivery and handler threads.
 *
 * Every message is bound to its existing packet class, so code (and addons) sending these classes through the
 * legacy `SimplePacketNetworking` API keep working and get these codecs.
 *
 * Bump [CHANNEL_VERSION] whenever any codec here changes.
 */
object VsMessages {
    const val CHANNEL_VERSION = 1

    lateinit var PLAYER_DRIVING: VsNetworkMessageType<PacketPlayerDriving>; private set
    lateinit var STOP_CHUNK_UPDATES: VsNetworkMessageType<PacketStopChunkUpdates>; private set
    lateinit var RESTART_CHUNK_UPDATES: VsNetworkMessageType<PacketRestartChunkUpdates>; private set
    lateinit var SYNC_ENTITY_TYPES: VsNetworkMessageType<PacketSyncVSEntityTypes>; private set
    lateinit var SYNC_BLOCK_STATE_INFO: VsNetworkMessageType<PacketSyncBlockStateInfo>; private set
    lateinit var ENTITY_SHIP_MOTION: VsiNetworkMessageType<PacketEntityShipMotion>; private set
    lateinit var MOB_SHIP_ROTATION: VsNetworkMessageType<PacketMobShipRotation>; private set
    lateinit var PLAYER_SHIP_MOTION: VsNetworkMessageType<PacketPlayerShipMotion>; private set
    lateinit var CHANGE_KNOWN_SHIPS: VsNetworkMessageType<PacketChangeKnownShips>; private set
    lateinit var PHYSICS_DEBUG: VsNetworkMessageType<PacketPhysicsDebug>; private set
    lateinit var FLUID_PARCEL: VsNetworkMessageType<PacketFluidParcel>; private set
    lateinit var FLUID_PARCEL_BATCH: VsNetworkMessageType<PacketFluidParcelBatch>; private set
    lateinit var GRAVITRON_FREEZE: VsNetworkMessageType<PacketGravitronFreeze>; private set

    fun register(vsApi: VsiCore) {
        val fields = VsMessageCodecs(vsApi)
        val ch = vsApi.networkChannel(ValkyrienSkiesMod.MOD_ID, CHANNEL_VERSION, required = true)

        PLAYER_DRIVING = ch.message("player_driving",
            vsApi.newCompositeCodec(
                vsApi.vector3fCodec.map({ Vector3f(it) }, { it }), PacketPlayerDriving::impulse,
                vsApi.booleanCodec, PacketPlayerDriving::sprint,
                vsApi.booleanCodec, PacketPlayerDriving::cruise,
                ::PacketPlayerDriving
            ),
            VsNetworkDirection.TO_SERVER, vsApi.latestDelivery, vsApi.gameHandlerThread, latestKey = { "driving" },
            boundClass = PacketPlayerDriving::class.java)

        // Stop/Restart must reach the client before the chunk packets they guard: sent immediately, handled in
        // arrival order on the game thread (the same queue vanilla chunk packets use).
        STOP_CHUNK_UPDATES = ch.message("stop_chunk_updates",
            vsApi.newCompositeCodec(fields.CHUNKS, PacketStopChunkUpdates::chunks, ::PacketStopChunkUpdates),
            VsNetworkDirection.TO_CLIENT, vsApi.reliableDelivery, vsApi.gameHandlerThread, flushImmediately = true,
            boundClass = PacketStopChunkUpdates::class.java)
        RESTART_CHUNK_UPDATES = ch.message("restart_chunk_updates",
            vsApi.newCompositeCodec(fields.CHUNKS, PacketRestartChunkUpdates::chunks, ::PacketRestartChunkUpdates),
            VsNetworkDirection.TO_CLIENT, vsApi.reliableDelivery, vsApi.gameHandlerThread, flushImmediately = true,
            boundClass = PacketRestartChunkUpdates::class.java)

        SYNC_ENTITY_TYPES = ch.message("sync_entity_types",
            vsApi.newCompositeCodec(vsApi.newMapCodec(vsApi.varIntCodec, vsApi.newStringCodec(512), 1 shl 20),
                PacketSyncVSEntityTypes::entity2Handler, ::PacketSyncVSEntityTypes),
            VsNetworkDirection.TO_CLIENT, boundClass = PacketSyncVSEntityTypes::class.java)

        SYNC_BLOCK_STATE_INFO = ch.message("sync_block_state_info", fields.SYNC_BLOCK_STATE_INFO_CODEC,
            VsNetworkDirection.TO_CLIENT, boundClass = PacketSyncBlockStateInfo::class.java)

        ENTITY_SHIP_MOTION = ch.message("entity_ship_motion", fields.ENTITY_SHIP_MOTION_CODEC,
            VsNetworkDirection.TO_CLIENT, vsApi.latestDelivery, vsApi.gameHandlerThread, latestKey = { it.entityID },
            boundClass = PacketEntityShipMotion::class.java)

        MOB_SHIP_ROTATION = ch.message("mob_ship_rotation",
            vsApi.newCompositeCodec(
                vsApi.varIntCodec, PacketMobShipRotation::entityID,
                vsApi.varLongCodec, PacketMobShipRotation::shipID,
                vsApi.doubleCodec, PacketMobShipRotation::yaw,
                vsApi.doubleCodec, PacketMobShipRotation::pitch,
                ::PacketMobShipRotation
            ),
            VsNetworkDirection.TO_CLIENT, vsApi.latestDelivery, vsApi.gameHandlerThread, latestKey = { it.entityID },
            boundClass = PacketMobShipRotation::class.java)

        PLAYER_SHIP_MOTION = ch.message("player_ship_motion", fields.PLAYER_SHIP_MOTION_CODEC,
            VsNetworkDirection.TO_SERVER, vsApi.latestDelivery, vsApi.gameHandlerThread, latestKey = { it.vehicleID },
            boundClass = PacketPlayerShipMotion::class.java)

        CHANGE_KNOWN_SHIPS = ch.message("change_known_ships",
            vsApi.newCompositeCodec(
                vsApi.booleanCodec, PacketChangeKnownShips::add,
                vsApi.newVarLongArrayCodec(1 shl 20), PacketChangeKnownShips::shipIDs,
                ::PacketChangeKnownShips
            ),
            VsNetworkDirection.TO_SERVER, boundClass = PacketChangeKnownShips::class.java)

        PHYSICS_DEBUG = ch.message("physics_debug",
            PhysicsDebugCodec,
            VsNetworkDirection.TO_CLIENT, vsApi.latestDelivery, vsApi.gameHandlerThread, latestKey = { "debug" },
            boundClass = PacketPhysicsDebug::class.java)

        // FluidParcelRenderer's queue is thread-safe and checks the connection itself: handle on arrival.
        FLUID_PARCEL = ch.message("fluid_parcel", fields.FLUID_PARCEL_CODEC,
            VsNetworkDirection.TO_CLIENT, vsApi.reliableDelivery, vsApi.networkHandlerThread, boundClass = PacketFluidParcel::class.java)
        FLUID_PARCEL_BATCH = ch.message("fluid_parcel_batch",
            vsApi.newCompositeCodec(
                vsApi.newStringCodec(256), PacketFluidParcelBatch::dimension,
                vsApi.newListCodec(fields.FLUID_PARCEL_UPDATE, PacketFluidParcelBatch.MAX_UPDATES), PacketFluidParcelBatch::updates,
                ::PacketFluidParcelBatch
            ),
            VsNetworkDirection.TO_CLIENT, vsApi.reliableDelivery, vsApi.networkHandlerThread, boundClass = PacketFluidParcelBatch::class.java)

        GRAVITRON_FREEZE = ch.message("gravitron_freeze",
            vsApi.newCompositeCodec(vsApi.newEnumCodec<InteractionHand>(), PacketGravitronFreeze::hand, ::PacketGravitronFreeze),
            VsNetworkDirection.TO_SERVER, boundClass = PacketGravitronFreeze::class.java)
    }
}

internal class VsMessageCodecs(vsApi: VsCoreApi) {

    /** Ship id or -1 ("no ship") as an unsigned varlong of id + 1. */
    private val OPTIONAL_SHIP_ID: VsCodec<Long> = vsApi.newCodec({ w, v -> w.writeVarLong(v + 1) }, { it.readVarLong() - 1 })

    private val VEC2I: VsCodec<Vector2i> = vsApi.newCodec(
        { w, v -> w.writeZigZagInt(v.x); w.writeZigZagInt(v.y) },
        { r -> Vector2i(r.readZigZagInt(), r.readZigZagInt()) }
    )
    val CHUNKS = vsApi.newListCodec(VEC2I, 1_000_000)

    val UUID_CODEC: VsCodec<UUID> = vsApi.newCodec(
        { w, v -> w.writeLong(v.mostSignificantBits); w.writeLong(v.leastSignificantBits) },
        { r -> UUID(r.readLong(), r.readLong()) }
    )

    private val CLIENT_BLOCK_INFO: VsCodec<ClientBlockInfo> = vsApi.newCompositeCodec(
        vsApi.doubleCodec, ClientBlockInfo::mass,
        vsApi.doubleCodec, ClientBlockInfo::friction,
        vsApi.doubleCodec, ClientBlockInfo::elasticity,
        ::ClientBlockInfo
    )

    internal val SYNC_BLOCK_STATE_INFO_CODEC: VsCodec<PacketSyncBlockStateInfo> = vsApi.newCompositeCodec(
        vsApi.newMapCodec(vsApi.newStringCodec(1024), CLIENT_BLOCK_INFO, 1 shl 22), PacketSyncBlockStateInfo::blockState2VS,
        vsApi.newMapCodec(vsApi.varIntCodec, vsApi.newStringCodec(512), 1 shl 22), PacketSyncBlockStateInfo::liquidState2Fluid,
        ::PacketSyncBlockStateInfo
    )

    internal val ENTITY_SHIP_MOTION_CODEC: VsCodec<PacketEntityShipMotion> = object : VsCodec<PacketEntityShipMotion> {
        override fun encode(writer: VsWriter, value: PacketEntityShipMotion) = with(value) {
            writer.writeVarInt(entityID)
            OPTIONAL_SHIP_ID.encode(writer, shipID)
            writer.writeDouble(x); writer.writeDouble(y); writer.writeDouble(z)
            writer.writeFloat(xVel.toFloat()); writer.writeFloat(yVel.toFloat()); writer.writeFloat(zVel.toFloat())
            writer.writeFloat(yRot.toFloat()); writer.writeFloat(xRot.toFloat()); writer.writeFloat(headYaw.toFloat())
            writer.writeBoolean(onGround)
            writer.writeDouble(worldX); writer.writeDouble(worldY); writer.writeDouble(worldZ)
            writer.writeVarInt(ticksSinceContact)
        }

        override fun decode(reader: VsReader): PacketEntityShipMotion {
            val entityId = reader.readVarInt()
            val shipId = OPTIONAL_SHIP_ID.decode(reader)
            val x = reader.readDouble(); val y = reader.readDouble(); val z = reader.readDouble()
            val xVel = reader.readFloat().toDouble(); val yVel = reader.readFloat().toDouble(); val zVel = reader.readFloat().toDouble()
            val yRot = reader.readFloat().toDouble(); val xRot = reader.readFloat().toDouble(); val headYaw = reader.readFloat().toDouble()
            val onGround = reader.readBoolean()
            val worldX = reader.readDouble(); val worldY = reader.readDouble(); val worldZ = reader.readDouble()
            val ticks = reader.readVarInt()
            return PacketEntityShipMotion(entityId, shipId, x, y, z, xVel, yVel, zVel, yRot, xRot, onGround,
                worldX, worldY, worldZ, headYaw, ticks)
        }
    }

    internal val PLAYER_SHIP_MOTION_CODEC: VsCodec<PacketPlayerShipMotion> = object : VsCodec<PacketPlayerShipMotion> {
        override fun encode(writer: VsWriter, value: PacketPlayerShipMotion) = with(value) {
            writer.writeVarLong(shipID)
            writer.writeDouble(x); writer.writeDouble(y); writer.writeDouble(z)
            writer.writeDouble(yRot)
            writer.writeFloat(xRot)
            writer.writeBoolean(onGround)
            writer.writeVarInt(ticksSinceContact)
            writer.writeVarInt(vehicleID + 1)
        }

        override fun decode(reader: VsReader) = PacketPlayerShipMotion(
            reader.readVarLong(), reader.readDouble(), reader.readDouble(), reader.readDouble(), reader.readDouble(),
            reader.readFloat(), reader.readBoolean(), reader.readVarInt(), reader.readVarInt() - 1
        )
    }

    private val FLUID_PARCEL_STATE: VsCodec<FluidParcelState> = object : VsCodec<FluidParcelState> {
        private val fluidName = vsApi.newStringCodec(256)
        override fun encode(writer: VsWriter, value: FluidParcelState) = with(value) {
            fluidName.encode(writer, this.fluid)
            writer.writeDouble(x); writer.writeDouble(y); writer.writeDouble(z)
            writer.writeDouble(velocityX); writer.writeDouble(velocityY); writer.writeDouble(velocityZ)
            writer.writeDouble(volume)
        }

        override fun decode(reader: VsReader) = FluidParcelState(
            fluidName.decode(reader), reader.readDouble(), reader.readDouble(), reader.readDouble(),
            reader.readDouble(), reader.readDouble(), reader.readDouble(), reader.readDouble()
        )
    }

    private val OPTIONAL_PARCEL_STATE = vsApi.newOptionalCodec(FLUID_PARCEL_STATE)

    val FLUID_PARCEL_CODEC: VsCodec<PacketFluidParcel> = vsApi.newCompositeCodec(
        UUID_CODEC, PacketFluidParcel::id,
        vsApi.newStringCodec(256), PacketFluidParcel::dimension,
        vsApi.varLongCodec, PacketFluidParcel::revision,
        OPTIONAL_PARCEL_STATE, PacketFluidParcel::state,
        ::PacketFluidParcel
    )

    val FLUID_PARCEL_UPDATE: VsCodec<FluidParcelUpdate> = vsApi.newCompositeCodec(
        UUID_CODEC, FluidParcelUpdate::id,
        vsApi.varLongCodec, FluidParcelUpdate::revision,
        OPTIONAL_PARCEL_STATE, FluidParcelUpdate::state,
        ::FluidParcelUpdate
    )
}

/** Encodes [PacketPhysicsDebug] for the operator-only physics debug view. Not for reuse: it uses Jackson CBOR, which is much larger and slower than a codec built with VsCoreApi's `new...Codec` functions. */
internal object PhysicsDebugCodec : VsCodec<PacketPhysicsDebug> {
    private val debugMapper = CBORMapper().registerKotlinModule().apply {
        setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
        setVisibility(PropertyAccessor.GETTER, Visibility.NONE)
        setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE)
        setVisibility(PropertyAccessor.SETTER, Visibility.NONE)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    override fun encode(writer: VsWriter, value: PacketPhysicsDebug) =
        writer.writeBytes(debugMapper.writeValueAsBytes(value))

    override fun decode(reader: VsReader): PacketPhysicsDebug =
        debugMapper.readValue(reader.readBytes(reader.remaining), PacketPhysicsDebug::class.java)
}
