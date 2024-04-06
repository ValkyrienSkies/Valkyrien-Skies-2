import org.valkyrienskies.aw2at.ATWriter
import org.valkyrienskies.aw2at.AWEntry
import org.valkyrienskies.aw2at.AWReader
import java.io.FileReader
import java.io.FileWriter

fun main() {
    val reader = AWReader(FileReader("common/src/main/resources/valkyrienskies-common.accesswidener"))
    val entries: List<AWEntry>
    try {
        reader.readSignature()
        entries = reader.readAll()
    } finally {
        reader.close()
    }

    val writer = ATWriter(FileWriter("forge/src/main/resources/META-INF/accesstransformer.cfg"))
    try {
        writer.writeAll(entries)
    } finally {
        writer.close()
    }
}
