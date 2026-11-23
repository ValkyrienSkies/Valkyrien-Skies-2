package org.valkyrienskies.mod.common.world

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Matrix4dc
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import java.util.Optional
import kotlin.math.sqrt

/** A world envelope for broad phase, with exact seat-local geometry for ProjectileUtil's two narrow-phase calls. */
class SeatHoverBox private constructor(private val native: AABB, private val basis: Matrix4dc,
                                       private val inverse: Matrix4dc, envelope: AABB) :
    AABB(envelope.minX, envelope.minY, envelope.minZ, envelope.maxX, envelope.maxY, envelope.maxZ) {
    private fun local(point: Vec3) = inverse.transformPosition(point.toJOML()).toMinecraft()
    override fun inflate(radius: Double): AABB = padded(native, basis, inverse, radius)
    override fun contains(point: Vec3) = native.contains(local(point))
    override fun clip(from: Vec3, to: Vec3): Optional<Vec3> = native.clip(local(from), local(to)).map {
        basis.transformPosition(it.toJOML()).toMinecraft()
    }

    companion object {
        @JvmStatic fun discoveryBounds(vanilla: AABB, from: Vec3, to: Vec3): AABB = vanilla.minmax(AABB(from, to))

        @JvmStatic fun of(box: AABB, basis: Matrix4dc, radius: Double): SeatHoverBox {
            return padded(box, basis, basis.invert(Matrix4d()), radius)
        }

        private fun padded(box: AABB, basis: Matrix4dc, m: Matrix4dc, radius: Double): SeatHoverBox {
            fun pad(x: Double, y: Double, z: Double) = radius * sqrt(x*x + y*y + z*z)
            val native = box.inflate(pad(m.m00(), m.m10(), m.m20()), pad(m.m01(), m.m11(), m.m21()), pad(m.m02(), m.m12(), m.m22()))
            return SeatHoverBox(native, basis, m, native.toJOML().transform(basis).toMinecraft())
        }

        // No root-vehicle exclusion here: these are candidates, not interaction decisions.
        @JvmStatic fun <T> supplement(existing: List<T>, presented: Iterable<T>, except: T,
                                      matches: java.util.function.Predicate<T>): List<T> {
            var result: ArrayList<T>? = null
            for (actor in presented) {
                if (actor === except || actor in existing || !matches.test(actor)) continue
                if (result == null) result = ArrayList(existing)
                result.add(actor)
            }
            return result ?: existing
        }
    }
}
