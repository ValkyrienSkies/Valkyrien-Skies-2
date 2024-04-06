package org.valkyrienskies.aw2at

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.enterprise.test.FileProperty
import java.io.File
import java.io.FileReader
import java.io.FileWriter

abstract class AW2ATTask : DefaultTask() {
    @get:InputFile
    abstract val accessWidener: Property<File>
    @get:OutputFile
    abstract val accessTransformer: Property<File>

    @TaskAction
    fun copy() {
        val inFile = accessWidener.get()
        val outFile = accessTransformer.get()
        val reader = AWReader(FileReader(inFile))
        val writer = ATWriter(FileWriter(outFile))
        val entries: List<AWEntry>
        try {
            reader.readSignature()
            entries = reader.readAll()
        } finally {
            reader.close()
        }

        try {
            writer.writeAll(entries)
        } finally {
            writer.close()
        }
    }
}
