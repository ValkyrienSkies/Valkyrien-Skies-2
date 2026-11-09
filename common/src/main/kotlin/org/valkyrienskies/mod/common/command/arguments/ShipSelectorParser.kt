package org.valkyrienskies.mod.common.command.arguments

import com.google.common.primitives.Doubles
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.advancements.critereon.MinMaxBounds
import net.minecraft.advancements.critereon.WrappedMinMaxBounds
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.api.toJOML
import org.valkyrienskies.mod.common.command.arguments.ContraptionSelectorOptions.suggestOptionValue
import org.valkyrienskies.mod.common.command.shipWorld
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.function.Predicate

class ShipSelectorParser(
    private val source: SharedSuggestionProvider?,
    val reader: StringReader
) {
    var suggestionProvider: (SuggestionsBuilder) -> CompletableFuture<Suggestions> = { it.buildFuture() }

    var amountLimit = 0

    var needsSpecificLevel = false

    var mass: MinMaxBounds.Doubles = MinMaxBounds.Doubles.ANY
    var size: MinMaxBounds.Doubles = MinMaxBounds.Doubles.ANY
    var isStatic: Boolean? = null

    var velocityX: MinMaxBounds.Doubles = MinMaxBounds.Doubles.ANY
    var velocityY: MinMaxBounds.Doubles = MinMaxBounds.Doubles.ANY
    var velocityZ: MinMaxBounds.Doubles = MinMaxBounds.Doubles.ANY
    var velocity: MinMaxBounds.Doubles = MinMaxBounds.Doubles.ANY

    var omegaX: MinMaxBounds.Doubles = MinMaxBounds.Doubles.ANY
    var omegaY: MinMaxBounds.Doubles = MinMaxBounds.Doubles.ANY
    var omegaZ: MinMaxBounds.Doubles = MinMaxBounds.Doubles.ANY
    var omega: MinMaxBounds.Doubles = MinMaxBounds.Doubles.ANY

    var distance: MinMaxBounds.Doubles = MinMaxBounds.Doubles.ANY
    var x: Double? = null
    var y: Double? = null
    var z: Double? = null
    var deltaX: Double? = null
    var deltaY: Double? = null
    var deltaZ: Double? = null
    var rotX: WrappedMinMaxBounds = WrappedMinMaxBounds.ANY
    var rotY: WrappedMinMaxBounds = WrappedMinMaxBounds.ANY
    var rotZ: WrappedMinMaxBounds = WrappedMinMaxBounds.ANY
    private var predicate = Predicate { _: ServerShip -> true }

    var customSort: (Vec3, Sequence<Ship>) -> Sequence<Ship> = ShipSelector.ORDER_ARBITRARY

    var slug: String? = null
    var notSlug: String? = null
    var id: Long? = null

    private var startingCursorIndex = 0

    fun shouldInvertValue(): Boolean {
        this.reader.skipWhitespace()
        if (this.reader.canRead() && this.reader.peek() == '!') {
            this.reader.skip()
            this.reader.skipWhitespace()
            return true
        } else {
            return false
        }
    }

    /**
     * Overwrites the [suggestionProvider] with a new lambda.
     * That lambda will be called after all parsing has finished,
     * so the last remaining [suggestionProvider] will take priority.
     *
     * Use the [SharedSuggestionProvider] from the lambda to add suggestions.
     */
    private fun suggest(block: (SuggestionsBuilder, SharedSuggestionProvider) -> Unit) {
        if (source != null) suggestionProvider = { block(it, source); it.buildFuture() }
    }

    val selector: ShipSelector
        get() {
            val aabb: AABB?
            if (this.deltaX == null && this.deltaY == null && this.deltaZ == null) {
                if (this.distance.getMax() != null) {
                    val d0: Double = this.distance.getMax()!!
                    aabb = AABB(-d0, -d0, -d0, d0 + 1.0, d0 + 1.0, d0 + 1.0)
                } else {
                    aabb = null
                }
            } else {
                aabb = this.createAabb(
                    (if (this.deltaX == null) 0.0 else this.deltaX)!!,
                    (if (this.deltaY == null) 0.0 else this.deltaY)!!, (if (this.deltaZ == null) 0.0 else this.deltaZ)!!
                )
            }

            val posFunc: Function<Vec3, Vec3>
            if (this.x == null && this.y == null && this.z == null) {
                posFunc = Function { pos: Vec3 -> pos }
            } else {
                posFunc = Function { pos: Vec3 ->
                    Vec3(
                        (if (this.x == null) pos.x else this.x)!!, (if (this.y == null) pos.y else this.y)!!,
                        (if (this.z == null) pos.z else this.z)!!
                    )
                }
            }

            return ShipSelector(
                this.amountLimit,
                this.needsSpecificLevel,
                this.predicate,
                this.distance,
                this.mass,
                this.size,
                posFunc,
                aabb,
                this.customSort,
                this.isStatic
            )
        }

    private fun createAabb(pSizeX: Double, pSizeY: Double, pSizeZ: Double): AABB {
        val flag = pSizeX < 0.0
        val flag1 = pSizeY < 0.0
        val flag2 = pSizeZ < 0.0
        val d0 = if (flag) pSizeX else 0.0
        val d1 = if (flag1) pSizeY else 0.0
        val d2 = if (flag2) pSizeZ else 0.0
        val d3 = (if (flag) 0.0 else pSizeX) + 1.0
        val d4 = (if (flag1) 0.0 else pSizeY) + 1.0
        val d5 = (if (flag2) 0.0 else pSizeZ) + 1.0
        return AABB(d0, d1, d2, d3, d4, d5)
    }

    /**
     * Add all the required predicates to [predicate] for our selector values (if present):
     * - [slug]
     * - [notSlug]
     * - [id]
     * - [rotX], [rotY], [rotZ]
     * - [velocity], [velocityX], [velocityY], [velocityZ]
     * - [omega], [omegaX], [omegaY], [omegaZ]
     *
     * The predicates which require a position ([customSort], [distance], [x], [y], [z], [deltaX], [deltaY], and [deltaZ])
     * are all handled in [ShipSelector.addPositionalPredicates]
     *
     * And the predicates which require a level ([needsSpecificLevel]) are handled in [ShipSelector.select]
     */
    fun finalizePredicates() {
        if (this.slug != null) {
            this.predicate = this.predicate.and { ship ->
                ship.slug == this.slug
            }
        }

        if (this.notSlug != null) {
            this.predicate = this.predicate.and { ship ->
                ship.slug != this.notSlug
            }
        }

        if (this.id != null) {
            this.predicate = this.predicate.and { ship ->
                ship.id == this.id
            }
        }

        if (this.rotX !== WrappedMinMaxBounds.ANY) {
            this.predicate = this.predicate.and(this.createRotationPredicate(this.rotX) { ship ->
                ship.transform.shipToWorldRotation.x()
            })
        }

        if (this.rotY !== WrappedMinMaxBounds.ANY) {
            this.predicate = this.predicate.and(this.createRotationPredicate(this.rotY) { ship ->
                ship.transform.shipToWorldRotation.y()
            })
        }

        if (this.rotY !== WrappedMinMaxBounds.ANY) {
            this.predicate = this.predicate.and(this.createRotationPredicate(this.rotZ) { ship ->
                ship.transform.shipToWorldRotation.z()
            })
        }

        if (this.velocity !== MinMaxBounds.Doubles.ANY) {
            this.predicate = this.predicate.and(Predicate { ship: Ship ->
                this.velocity.matches(
                    ship.velocity.length()
                )
            })
        }

        if (this.velocityX !== MinMaxBounds.Doubles.ANY || this.velocityY !== MinMaxBounds.Doubles.ANY || this.velocityZ !== MinMaxBounds.Doubles.ANY) {
            this.predicate = this.predicate.and(Predicate { ship: Ship ->
                velocityX.matches(ship.velocity.x()) &&
                    velocityY.matches(ship.velocity.y()) &&
                    velocityZ.matches(ship.velocity.z())
            })
        }

        if (this.omega !== MinMaxBounds.Doubles.ANY) {
            this.predicate = this.predicate.and(Predicate { ship: Ship ->
                this.omega.matches(
                    Math.toDegrees(ship.angularVelocity.length())
                )
            })
        }

        if (this.omegaX !== MinMaxBounds.Doubles.ANY || this.omegaY !== MinMaxBounds.Doubles.ANY || this.omegaZ !== MinMaxBounds.Doubles.ANY) {
            this.predicate = this.predicate.and(Predicate { ship: Ship ->
                omegaX.matches(ship.angularVelocity.x() / (2.0 * Math.PI)) &&
                    omegaY.matches(ship.angularVelocity.y() / (2.0 * Math.PI)) &&
                    omegaZ.matches(ship.angularVelocity.z() / (2.0 * Math.PI))
            })
        }
    }

    private fun createRotationPredicate(
        pAngleBounds: WrappedMinMaxBounds, pAngleFunction: (Ship) -> Double
    ): Predicate<Ship> {
        val d0 = Mth.wrapDegrees((pAngleBounds.min ?: 0.0f)).toDouble()
        val d1 = Mth.wrapDegrees((pAngleBounds.max ?: 359.0f)).toDouble()
        return Predicate { ship: Ship ->
            val d2 = Mth.wrapDegrees(pAngleFunction(ship))
            if (d0 > d1) {
                return@Predicate d2 >= d0 || d2 <= d1
            } else {
                return@Predicate d2 in d0..d1
            }
        }
    }

    /**
     * Suggests the `@v` or nearby slugs. Calls [parseSelector] if `@` is already typed
     */
    @Throws(CommandSyntaxException::class)
    fun parse(selectorOnly: Boolean): ShipSelector {
        this.startingCursorIndex = this.reader.cursor
        suggest { builder, provider ->
            if ("@v".startsWith(builder.remaining)) {
                builder.suggest("@v")
            }
            if (!selectorOnly) {
                source?.let { s ->
                    s.shipWorld.allShips
                    .mapNotNull { it.slug }
                    .filter { it.startsWith(builder.remaining) }
                    .forEach { builder.suggest(it) }
                }
            }
        }
        if (this.reader.canRead() && this.reader.peek() == '@') {
            this.reader.skip()
            this.parseSelector()
        }  else if (!selectorOnly) {
            // Reset cursor
            reader.cursor = startingCursorIndex
            slug = reader.readUnquotedString()
        }

        this.finalizePredicates()
        return this.selector
    }

    /**
     * Suggests the `@v[]` and calls [parseOptions] for between the square brackets
     */
    @Throws(CommandSyntaxException::class)
    private fun parseSelector() {
        suggest { builder, provider -> builder.suggest("v") }
        if (!this.reader.canRead()) {
            throw ERROR_MISSING_SELECTOR_TYPE.createWithContext(this.reader)
        } else {
            val selectorType = this.reader.read()
            if (selectorType != 'v') {
                this.reader.cursor = this.reader.cursor - 1
                throw ERROR_UNKNOWN_SELECTOR_TYPE.createWithContext(this.reader, selectorType)
            }

            suggest { builder, provider -> builder.suggest("[") }

            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.reader.skip()
                suggest { builder, provider ->
                    builder.suggest("]")
                    ContraptionSelectorOptions.suggestNames(this, builder)
                }
                this.parseOptions()
            }
        }
    }

    /**
     * Parses the options between the square brackets `[]`
     */
    @Throws(CommandSyntaxException::class)
    private fun parseOptions() {
        this.reader.skipWhitespace()

        while (true) {
            if (this.reader.canRead() && this.reader.peek() != ']') {
                this.reader.skipWhitespace()
                val i = this.reader.getCursor()
                val s = this.reader.readString()
                val `entityselectoroptions$modifier` =
                    ContraptionSelectorOptions.get(this, s, i)
                this.reader.skipWhitespace()
                if (!this.reader.canRead() || this.reader.peek() != '=') {
                    this.reader.cursor = i
                    throw ERROR_EXPECTED_OPTION_VALUE.createWithContext(this.reader, s)
                }

                this.reader.skip()
                this.reader.skipWhitespace()

                suggest { _, _ ->  }

                this.reader.skipWhitespace()

                suggestionProvider = { builder ->
                    val valueFuture = suggestOptionValue(source, builder)?.buildFuture()
                    if (valueFuture != null) valueFuture
                    else {
                        builder.suggest(','.toString())
                        builder.suggest(']'.toString())
                        builder.buildFuture()
                    }
                }

                `entityselectoroptions$modifier`(this)

                if (!this.reader.canRead()) {
                    continue
                }

                if (this.reader.peek() == ',') {
                    this.reader.skip()

                    suggest { builder, provider -> ContraptionSelectorOptions.suggestNames(this, builder) }

                    continue
                }

                if (this.reader.peek() != ']') {
                    throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(this.reader)
                }
            }

            if (this.reader.canRead()) {
                this.reader.skip()

                suggest { _, _ ->  }

                return
            }

            throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(this.reader)
        }
    }

    companion object {
        private val ERROR_MISSING_SELECTOR_TYPE =
            SimpleCommandExceptionType(Component.translatable("argument.valkyrienskies.ship.selector.missing"))
        private val ERROR_UNKNOWN_SELECTOR_TYPE = DynamicCommandExceptionType { obj: Any? ->
            Component.translatable("argument.valkyrienskies.ship.selector.unknown", obj)
        }
        private val ERROR_EXPECTED_END_OF_OPTIONS = SimpleCommandExceptionType(
            Component.translatable("argument.valkyrienskies.ship.options.unterminated")
        )
        private val ERROR_EXPECTED_OPTION_VALUE = DynamicCommandExceptionType { obj: Any? ->
            Component.translatable(
                "argument.valkyrienskies.ship.options.valueless", obj
            )
        }

        val ORDER_NEAREST: (Vec3, Sequence<Ship>) -> Sequence<Ship> =
            { pos: Vec3, sort: Sequence<Ship> ->
                sort.sortedWith { s1, s2 -> Doubles.compare(s1.transform.positionInWorld.distance(pos.toJOML()), s2.transform.positionInWorld.distance(pos.toJOML())) }
            }
        val ORDER_FURTHEST:  (Vec3, Sequence<Ship>) -> Sequence<Ship> =
            { pos: Vec3, sort: Sequence<Ship> ->
                sort.sortedWith { s1, s2 -> Doubles.compare(s2.transform.positionInWorld.distance(pos.toJOML()), s1.transform.positionInWorld.distance(pos.toJOML())) }
            }
        val ORDER_RANDOM: (Vec3, Sequence<Ship>) -> Sequence<Ship> =
            { pos: Vec3, sort: Sequence<Ship> ->
                sort.shuffled()
            }
    }
}
