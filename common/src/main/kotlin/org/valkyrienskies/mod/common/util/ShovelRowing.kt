package org.valkyrienskies.mod.common.util

import com.fasterxml.jackson.annotation.JsonIgnore
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ShovelItem
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import org.joml.Vector3d
import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ShipPhysicsListener
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.getShipStoodOn
import org.valkyrienskies.mod.common.shipObjectWorld
import java.util.concurrent.ConcurrentLinkedQueue

class ShovelRowingAttachment : ShipPhysicsListener {
    private data class Stroke(val impulse: Vector3d, val position: Vector3d)
    @JsonIgnore
    private val strokes = ConcurrentLinkedQueue<Stroke>()

    fun addStroke(impulse: Vector3d, position: Vector3d) {
        strokes.add(Stroke(Vector3d(impulse), Vector3d(position)))
    }

    override fun physTick(physShip: PhysShip, physLevel: PhysLevel) = Unit

    override fun physTick(physShip: PhysShip, physLevel: PhysLevel, delta: Double) {
        if (!delta.isFinite() || delta <= 0.0) return
        while (true) {
            val stroke = strokes.poll() ?: break
            if (!physShip.isStatic) physShip.applyWorldForce(stroke.impulse.div(delta), stroke.position)
        }
    }
}

object ShovelRowing {
    @JvmStatic
    fun tryStroke(player: Player, hand: InteractionHand): Boolean {
        val config = VSGameConfig.SERVER.ShipInteractions
        val stack = player.getItemInHand(hand)
        if (!config.shovelRowing || stack.item !is ShovelItem || player.isSpectator) return false
        val standingShip = player.getShipStoodOn() ?: return false
        val ship = if (player.level() is ServerLevel)
            player.level().shipObjectWorld.loadedShips.getById(standingShip.id) as? LoadedServerShip else null
        if (!player.level().isClientSide && (ship == null || ship.isStatic)) return false
        val eye = player.eyePosition
        val look = player.lookAngle
        val hit = player.level().clip(ClipContext(eye, eye.add(look.scale(4.5)),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player))
        if (hit.type != HitResult.Type.BLOCK ||
            !player.level().getFluidState(hit.blockPos).`is`(FluidTags.WATER)) return false
        val direction = Vector3d(look.x, 0.0, look.z)
        if (direction.lengthSquared() < 1.0e-6) return false
        if (player.cooldowns.isOnCooldown(stack.item)) return true
        if (player.level() is ServerLevel && ship != null) {
            val impulse = config.rowingImpulse
            if (!impulse.isFinite() || impulse <= 0.0) return false
            val attachment = ship.getAttachment<ShovelRowingAttachment>() ?: ShovelRowingAttachment().also {
                ship.setAttachment(it)
            }
            attachment.addStroke(direction.normalize().mul(impulse), hit.location.toJOML())
            player.cooldowns.addCooldown(stack.item, config.rowingCooldownTicks.coerceAtLeast(1))
            stack.hurtAndBreak(1, player) { it.broadcastBreakEvent(hand) }
            (player.level() as ServerLevel).sendParticles(ParticleTypes.SPLASH,
                hit.location.x, hit.location.y, hit.location.z, 8, 0.15, 0.05, 0.15, 0.05)
        }
        return true
    }
}
