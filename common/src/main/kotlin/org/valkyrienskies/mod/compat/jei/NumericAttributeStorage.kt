package org.valkyrienskies.mod.compat.jei

import mezz.jei.core.search.ISearchStorage
import java.util.Collections
import java.util.IdentityHashMap
import java.util.NavigableMap
import java.util.TreeMap
import java.util.function.Consumer
import java.util.function.ToIntFunction
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/**
 * Yes this is a massive file I'm sorry
 */
class NumericAttributeStorage<T> : ISearchStorage<T> {

    // attribute name -> (attribute value -> items with that amount)
    // e.g. {"kg": {5: ["minecraft:oak_log"]}}
    private val index: MutableMap<String, NavigableMap<Double, MutableSet<T>>> =
        HashMap<String, NavigableMap<Double, MutableSet<T>>>()

    private val allElements: MutableSet<T> = Collections.newSetFromMap<T>(IdentityHashMap<T, Boolean>())

    private var indexedKeyCount = 0
    private var unparseableKeyCount = 0

    init {
        for (attribute in ATTRIBUTES) {
            this.index[attribute] = TreeMap<Double, MutableSet<T>>()
        }
    }

    /**
     * Put is called by JEI. When giving JEI your list of keys for this, they should be in the rough format of
     * `f:%s e:%s kg:%s`
     *
     * I'm still not 100% sure what class T is, but it's something equivalent to an item and JEI hands it to us to search with.
     */
    override fun put(key: String, value: T) {
        this.allElements.add(value)

        var indexedAnything = false
        val matcher = INDEX_PAIR.matcher(key)
        while (matcher.find()) {
            val name = matcher.group(1).lowercase()

            val byValue = this.index[name] ?: continue // unknown attribute

            val parsed: Double
            try {
                parsed = matcher.group(2).toDouble()
            } catch (_: NumberFormatException) {
                continue
            }

            // if this attribute has no items for that numeric value yet,
            // make an empty list of items and add the value to it
            byValue.computeIfAbsent(parsed) { _: Double ->
                Collections.newSetFromMap<T>(
                    IdentityHashMap<T, Boolean>()
                )
            }.add(value)

            indexedAnything = true
        }

        if (indexedAnything) {
            this.indexedKeyCount++
        } else {
            this.unparseableKeyCount++
        }
    }

    override fun getSearchResults(token: String, resultsConsumer: Consumer<MutableCollection<T>>) {
        val constraints = parseQuery(token)
        if (constraints.isEmpty()) {
            return
        }

        // Search each attribute separately, exit early if one has no results
        val candidateSets: MutableList<MutableSet<T>> = ArrayList(constraints.size)
        for (constraint in constraints) {
            val matches = matchingElements(constraint)
            if (matches.isEmpty()) {
                return
            }
            candidateSets.add(matches)
        }

        // smallest-first so retainAll iterates as few values as possible.
        candidateSets.sortWith(
            Comparator.comparingInt<MutableSet<T>>(ToIntFunction { obj: MutableSet<T> -> obj.size })
        )
        val iterator = candidateSets.iterator()
        val result = Collections.newSetFromMap<T>(IdentityHashMap())
        result.addAll(iterator.next())
        while (iterator.hasNext() && !result.isEmpty()) {
            result.retainAll(iterator.next())
        }

        if (!result.isEmpty()) {
            resultsConsumer.accept(result)
        }
    }

    override fun getAllElements(resultsConsumer: Consumer<MutableCollection<T>>) {
        resultsConsumer.accept(this.allElements)
    }

    // This is just used for JEI debugging
    // Ngl I just had claude write this function
    override fun statistics(): String {
        val builder = StringBuilder("NumericAttributeStorage:\n")
        builder.append("  elements: ").append(this.allElements.size).append('\n')
        builder.append("  keys indexed: ").append(this.indexedKeyCount)
            .append(", keys with no attributes: ").append(this.unparseableKeyCount).append('\n')
        for (attribute in ATTRIBUTES) {
            val byValue = this.index[attribute]
            var elements = 0
            byValue?.let {
                for (set in it.values) {
                    elements += set.size
                }
            }
            builder.append("  ").append(attribute)
                .append(": ").append(byValue?.size).append(" distinct values, ")
                .append(elements).append(" entries\n")
        }
        return builder.toString()
    }

    // Get a set of all items we have stored that match the attribute constraint
    private fun matchingElements(constraint: Constraint): MutableSet<T> {
        val byValue = this.index[constraint.attribute]
        if (byValue == null || byValue.isEmpty()) {
            return mutableSetOf()
        }
        if (constraint.min > constraint.max) {
            return mutableSetOf()
        }

        val range: NavigableMap<Double, MutableSet<T>> = byValue.subMap(
            constraint.min, constraint.includeMin,
            constraint.max, constraint.includeMax
        )
        if (range.isEmpty()) {
            return mutableSetOf()
        }
        if (range.size == 1) {
            // an exact match with only one result
            return range.firstEntry().value
        }

        val union = Collections.newSetFromMap<T>(IdentityHashMap())
        for (set in range.values) {
            union.addAll(set)
        }
        return union
    }

    private data class Constraint(
        val attribute: String,
        val min: Double, val includeMin: Boolean,
        val max: Double, val includeMax: Boolean
    )

    companion object {
        private val ATTRIBUTES = mutableListOf<String>("f", "e", "m")
        private val DEFAULT_ATTRIBUTE = "m"

        // Matches a key:value syntax
        private val INDEX_PAIR: Pattern = Pattern.compile(
            "([a-z]+)\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE
        )

        // The regex groups here are: name (e.g. f/e/kg/etc), operator (e.g. = <= > .. etc), first number, second number (optional, used for ranges)
        private val QUERY_TERM: Pattern = Pattern.compile(
            "([a-z]+)\\s*:\\s*(>=|<=|>|<|=)?\\s*(-?\\d+(?:\\.\\d+)?)"
                + "(?:\\s*\\.\\.\\s*(-?\\d+(?:\\.\\d+)?))?",
            Pattern.CASE_INSENSITIVE
        )

        // Matches if just a number was passed in, no (key:blabla)
        private val BARE_TERM: Pattern = Pattern.compile(
            "^\\s*\\(?\\s*(>=|<=|>|<|=)?\\s*(-?\\d+(?:\\.\\d+)?)" +
                "(?:\\s*\\.\\.\\s*(-?\\d+(?:\\.\\d+)?))?\\s*\\)?\\s*$"
        )

        // Convert from the string we pass around (f:%s e:%s kg:%s) into an actual object
        private fun parseQuery(token: String): MutableList<Constraint> {
            val constraints: MutableList<Constraint> = ArrayList()

            val matcher = QUERY_TERM.matcher(token)
            while (matcher.find()) {
                val name = matcher.group(1).lowercase()
                if (!ATTRIBUTES.contains(name)) {
                    continue
                }
                buildConstraint(name, matcher.group(2), matcher.group(3), matcher.group(4))
                    ?.let { constraints.add(it) }
            }

            // they just passed in a number, no key, so use it as mass
            if (constraints.isEmpty()) {
                val bare = BARE_TERM.matcher(token)
                if (bare.matches()) {
                    buildConstraint(DEFAULT_ATTRIBUTE, bare.group(1), bare.group(2), bare.group(3))
                        ?.let { constraints.add(it) }
                }
            }

            return constraints
        }

        /**
         * Turn an attribute (name), operator (= <= > etc) or range (firstText .. secondText) into a Constraint
         */
        private fun buildConstraint(
            name: String,
            operator: String?,
            firstText: String,
            secondText: String?
        ): Constraint? {
            val first = firstText.toDoubleOrNull() ?: return null

            if (secondText != null) { // x..y where secondText is the y
                val second = secondText.toDoubleOrNull() ?: return null
                return Constraint(name, min(first, second), true, max(first, second), true)
            }

            return when (operator ?: "=") {
                ">" -> Constraint(name, first, false, Double.POSITIVE_INFINITY, true)
                ">=" -> Constraint(name, first, true, Double.POSITIVE_INFINITY, true)
                "<" -> Constraint(name, Double.NEGATIVE_INFINITY, true, first, false)
                "<=" -> Constraint(name, Double.NEGATIVE_INFINITY, true, first, true)
                else -> Constraint(name, first, true, first, true)
            }
        }
    }
}
