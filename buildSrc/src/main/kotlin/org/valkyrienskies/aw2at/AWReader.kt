package org.valkyrienskies.aw2at

import java.io.Closeable
import java.io.Reader
import java.lang.StringBuilder
import java.util.Locale
import java.util.UnknownFormatFlagsException

class AWReader(val reader: Reader): Closeable {
    lateinit var line: String
    val builder = StringBuilder()
    var position = 0

    fun readSignature() {
        var c = Char(reader.read())
        while (c != '\n') {
            if (!c.isWhitespace())
                builder.append(c)

            c = Char(reader.read())
        }

        if (builder.toString() != "accessWidenerv1named")
            throw UnknownFormatFlagsException("File doesn't start correctly")
        builder.clear()
    }

    fun readAll(): List<AWEntry> {
        val result = mutableListOf<AWEntry>()
        reader.forEachLine { it ->
            line = it.trim()
            position = 0
            read()?.let { result.add(it) }
        }

        return result
    }

    fun read(): AWEntry? {
        if (line.isBlank()) return null
        if (line.startsWith("#")) return null

        val modifierString = readUntilWhitespace()
        val modification = AccessModification.valueOf(modifierString.uppercase(Locale.getDefault()))
        val type = readUntilWhitespace().lowercase(Locale.getDefault())

        return when (type) {
            "class" -> readClass(modification)
            "method" -> readMethod(modification)
            "field" -> readField(modification)
            else -> throw UnknownFormatFlagsException(type)
        }
    }

    private fun readClass(modification: AccessModification): ClassEntry {
        val clazz = readUntilWhitespace()

        return ClassEntry(modification, clazz)
    }

    private fun readMethod(modification: AccessModification): MethodEntry {
        val clazz = readUntilWhitespace()
        val method = readUntilWhitespace()

        return MethodEntry(modification, clazz, method)
    }

    private fun readField(modification: AccessModification): FieldEntry {
        val clazz = readUntilWhitespace()
        val fieldName = readUntilWhitespace()
        val fieldType = readUntilWhitespace()

        return FieldEntry(modification, clazz, fieldName, fieldType)
    }

    private fun readUntilWhitespace(): String {
        if (line.isEmpty()) return ""
        builder.clear()

        while (position < line.length && line[position].isWhitespace())
            position++

        while (position < line.length && !line[position].isWhitespace())
            builder.append(line[position++])

        return builder.toString()
    }

    override fun close() {
        reader.close()
    }
}
