package org.valkyrienskies.mod.compat.immersiveportals

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.api.portals.ShipPortalMouth
import org.valkyrienskies.mod.api.portals.ShipPortalPair
import org.valkyrienskies.mod.api.portals.ShipPortals
import qouteall.imm_ptl.core.portal.Portal
import qouteall.imm_ptl.core.portal.PortalExtension
import qouteall.imm_ptl.core.portal.PortalManipulation
import java.util.UUID

object ImmersiveShipPortals {
    private class Binding(val a: UUID, val b: UUID, val level: ServerLevel) {
        val key = ResourceLocation("immersive_portals", "$a/$b")
    }

    private val members = HashMap<UUID, Binding>()

    fun init() {
        Portal.serverPortalTickSignal.connect(::observe)
        Portal.portalDisposeSignal.connect { portal ->
            if (portal.level() is ServerLevel && portal.removalReason?.shouldDestroy() == true) retire(portal.uuid)
        }
    }

    @JvmStatic
    fun clear() = members.clear()

    private fun retire(id: UUID) {
        val binding = members[id] ?: return
        members.remove(binding.a)
        members.remove(binding.b)
        ShipPortals.remove(binding.level, binding.key)
    }

    private fun supported(p: Portal): Boolean =
        p.teleportable && p.specificPlayerId == null && !p.isGlobalPortal && p.specialShape == null &&
            p.scaling == 1.0 && p.dimensionTo == p.level().dimension() && p.width.isFinite() && p.width > 0 &&
            p.height.isFinite() && p.height > 0 && !p.animation.hasAnimationDriver() && p.isPortalValid() &&
            ShipPortalMouth.validAxes(p.axisW, p.axisH)

    private fun observe(portal: Portal) {
        if (!ShipPortals.isSupported()) return
        if (!supported(portal)) {
            retire(portal.uuid)
            return
        }
        val level = portal.level() as ServerLevel
        val existing = members[portal.uuid]
        val other = PortalExtension.get(portal).reversePortal ?: existing?.let {
            level.getEntity(if (portal.uuid == it.a) it.b else it.a) as? Portal
        } ?: PortalManipulation.findReversePortal(portal)
        if (other == null) return
        if (!supported(other) || !reciprocal(portal, other)) {
            retire(portal.uuid)
            return
        }
        val a = if (portal.uuid < other.uuid) portal else other
        val b = if (portal === a) other else portal
        val binding = if (existing?.a == a.uuid && existing.b == b.uuid && existing.level === level) existing else {
            retire(a.uuid)
            retire(b.uuid)
            Binding(a.uuid, b.uuid, level).also { members[a.uuid] = it; members[b.uuid] = it }
        }
        ShipPortals.set(level, binding.key, ShipPortalPair(
            ShipPortalMouth(a.originPos, a.axisW, a.axisH), ShipPortalMouth(b.originPos, b.axisW, b.axisH),
            a.width * 0.5, a.height * 0.5))
    }

    private fun reciprocal(a: Portal, b: Portal): Boolean {
        fun close(v: Vec3, w: Vec3) = v.distanceToSqr(w) < 1e-12
        return a !== b && a.width == b.width && a.height == b.height &&
            close(a.destPos, b.originPos) && close(b.destPos, a.originPos) &&
            close(a.transformLocalVec(a.axisW), b.axisW.scale(-1.0)) && close(a.transformLocalVec(a.axisH), b.axisH) &&
            close(b.transformLocalVec(b.axisW), a.axisW.scale(-1.0)) && close(b.transformLocalVec(b.axisH), a.axisH)
    }
}
