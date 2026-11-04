package org.valkyrienskies.mod.common.fluid

import org.joml.Vector3d
import org.valkyrienskies.mod.common.networking.FluidParcelState
import org.valkyrienskies.mod.common.networking.PacketFluidParcel
import java.util.UUID

internal class FluidParcelClock {
    private var previous: Long? = null
    private var time = 0L

    fun reset() { previous = null; time = 0 }

    fun sample(wallTime: Long, paused: Boolean): Long {
        previous?.let { if (!paused) time += (wallTime - it).coerceAtLeast(0) }
        previous = wallTime
        return time
    }
}

internal class FluidParcelVisuals(private val capacity: Int = 4096) {
    internal data class Sample(val id: UUID, val state: FluidParcelState, val position: Vector3d)
    private data class Track(val revision: Long, val state: FluidParcelState?, val start: Vector3d,
        val received: Long, val duration: Long) {
        fun position(now: Long): Vector3d {
            val state = state ?: return Vector3d(start)
            val alpha = ((now - received).toDouble() / duration).coerceIn(0.0, 1.0)
            return Vector3d(start).lerp(Vector3d(state.x, state.y, state.z), alpha)
        }
    }
    private val tracks = LinkedHashMap<UUID, Track>()

    fun clear() = tracks.clear()

    fun accept(packet: PacketFluidParcel, now: Long) {
        expire(now)
        val old = tracks[packet.id]
        if (old != null && packet.revision <= old.revision) return
        val state = packet.state
        if (state != null && (!state.volume.isFinite() || state.volume <= 0 || state.fluid.length > 256 ||
                !state.x.isFinite() || !state.y.isFinite() || !state.z.isFinite() ||
                !state.velocityX.isFinite() || !state.velocityY.isFinite() || !state.velocityZ.isFinite())) return
        if (old == null && tracks.size >= capacity) {
            val retired = tracks.entries.firstOrNull { it.value.state == null } ?: return
            tracks.remove(retired.key)
        }
        val start = if (old?.state != null) old.position(now)
            else if (state != null) Vector3d(state.x, state.y, state.z) else Vector3d()
        val duration = if (old == null) 50_000_000L else (now - old.received).coerceIn(50_000_000L, 250_000_000L)
        tracks[packet.id] = Track(packet.revision, state, start, now, duration)
    }

    fun samples(now: Long): List<Sample> {
        expire(now)
        return tracks.mapNotNull { (id, track) -> track.state?.let { Sample(id, it, track.position(now)) } }
    }

    private fun expire(now: Long) {
        tracks.entries.removeIf { now - it.value.received > 10_000_000_000L }
    }
}
