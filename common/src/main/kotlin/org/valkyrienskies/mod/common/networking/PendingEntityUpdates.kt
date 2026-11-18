package org.valkyrienskies.mod.common.networking

class PendingEntityUpdates<T>(private val maxAge: Int = 100, private val capacity: Int = 2048) {
    private data class Entry<T>(val value: T, var age: Int = 0)
    private val updates = LinkedHashMap<Int, Entry<T>>()
    private var context: Any? = null

    fun useContext(value: Any?) {
        if (context !== value) {
            updates.clear()
            context = value
        }
    }

    fun put(entityId: Int, value: T) {
        updates.remove(entityId)
        updates[entityId] = Entry(value)
        if (updates.size > capacity) updates.remove(updates.keys.first())
    }

    fun remove(entityId: Int) {
        updates.remove(entityId)
    }

    fun tick(apply: (T) -> Boolean) {
        updates.entries.removeIf { (_, entry) -> apply(entry.value) || ++entry.age >= maxAge }
    }
}
