package org.valkyrienskies.aw2at

import org.valkyrienskies.aw2at.AccessModification.ACCESSIBLE
import org.valkyrienskies.aw2at.AccessModification.MUTABLE
import java.io.Closeable
import java.io.Writer

class ATWriter(val writer: Writer): Closeable {

    fun writeAll(list: List<AWEntry>) = list.forEach { write(it) }

    fun write(entry: AWEntry) = when(entry) {
        is ClassEntry -> writeClass(entry)
        is FieldEntry -> writeField(entry)
        is MethodEntry -> writeMethod(entry)
    }

    private fun writeModification(modification: AccessModification) {
        when (modification) {
            ACCESSIBLE -> writer.write("public")
            MUTABLE -> writer.write("public") //TODO ?!
        }
    }

    private fun writeClass(entry: ClassEntry) {
        writeModification(entry.modification)
        space()
        writeClassName(entry.clazz)
        nextLine()
    }

    private fun writeMethod(entry: MethodEntry) {
        writeModification(entry.modification)
        space()
        writeClassName(entry.clazz)
        space()
        writer.write(entry.method)
        nextLine()
    }

    private fun writeField(entry: FieldEntry) {
        writeModification(entry.modification)
        space()
        writeClassName(entry.clazz)
        space()
        writer.write(entry.fieldName)
        nextLine()
    }

    private fun writeClassName(name: String) {
        writer.write(name.replace('/', '.'))
    }

    private fun space() = writer.write(" ")
    private fun nextLine() = writer.write("\n")
    override fun close() {
        writer.close()
    }
}
