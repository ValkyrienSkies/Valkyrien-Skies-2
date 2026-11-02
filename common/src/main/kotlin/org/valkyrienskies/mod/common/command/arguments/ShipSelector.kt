package org.valkyrienskies.mod.common.command.arguments

import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.advancements.critereon.MinMaxBounds
import net.minecraft.commands.CommandSourceStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.core.api.ships.QueryableShipData
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.api.dimensionId
import org.valkyrienskies.mod.api.toMinecraft
import org.valkyrienskies.mod.common.toWorldCoordinates
import java.util.function.Function
import java.util.function.Predicate
import kotlin.math.min

class ShipSelector(
    val limit: Int,
    val needsSpecificLevel: Boolean,
    val predicate: Predicate<ServerShip>,
    val range: MinMaxBounds.Doubles,
    val mass: MinMaxBounds.Doubles,
    val size: MinMaxBounds.Doubles,
    val position: Function<Vec3, Vec3>,
    val aabb: AABB? = null,
    val order: (Vec3, Sequence<Ship>) -> Sequence<Ship>,
    val isStatic: Boolean? = null
) {

    private fun addPositionalPredicates(position: Vec3): Predicate<ServerShip> {
        var predicate = this.predicate
        if (this.aabb != null) {
            val aabb = this.aabb.move(position)
            predicate =
                predicate.and(Predicate { ship: ServerShip -> aabb.intersects(ship.worldAABB.toMinecraft()) })
        }

        if (!this.range.isAny) {
            predicate = predicate.and(
                Predicate { ship: ServerShip ->
                    this.range.matchesSqr(
                        position.distanceToSqr(
                            ship.transform.positionInWorld.toMinecraft()
                        )
                    )
                })
        }

        if (!this.mass.isAny) {
            predicate =
                predicate.and(Predicate { ship: ServerShip -> this.mass.matches(ship.inertiaData.mass) })
        }

        if (!this.size.isAny) {
            predicate =
                predicate.and(Predicate { ship: ServerShip -> this.size.matches(ship.worldAABB.toMinecraft().size) })
        }

        if (this.isStatic != null) {
            predicate = predicate.and(
                Predicate { ship: ServerShip -> this.isStatic == ship.isStatic })
        }

        return predicate
    }

    @Throws(CommandSyntaxException::class)
    fun select(source: CommandSourceStack, queryableShipData: QueryableShipData<Ship>): Sequence<Ship> {
        var found = queryableShipData.asSequence()

        // If we're doing distance checks, we only want to check the ships in the same dimension
        // Otherwise its weird behaviour
        if (this.needsSpecificLevel) {
            found = found.filter { it.chunkClaimDimension == source.level.dimensionId }
        }

        val position = this.position.apply(source.level.toWorldCoordinates(source.position))
        val predicate = this.addPositionalPredicates(position)

        // Do all the other selectors
        found = found.filter { ship -> predicate.test(ship as ServerShip) }

        return this.sortAndLimit(position, found)

    }

    private fun sortAndLimit(position: Vec3, ships: Sequence<Ship>): Sequence<Ship> {
        var ships = ships
        ships = this.order(position, ships)
        // If limit is 0 (aka we don't have a limit), then return all the ships
        val limit = if (this.limit != 0) this.limit else Int.MAX_VALUE
        return ships.take(min(limit, ships.count()))
    }

    companion object {
        val ORDER_ARBITRARY: (Vec3, Sequence<Ship>) -> Sequence<Ship> =
           { vec3, sequence -> sequence }
    }
}
