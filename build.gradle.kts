import org.valkyrienskies.extraProperty

plugins {
    id("org.valkyrienskies.maven-repo-conventions")
    id("architectury-plugin")
}

architectury {
    compileOnly()
}

val customReleaseVersion = extra.properties["CustomReleaseVersion"] as String?

// Determine the version
if (customReleaseVersion != null) {
    // Remove release/ from the version if present
    version = customReleaseVersion.replaceFirst("^release/".toRegex(), "")
} else {
    val gitRevision = "git rev-parse HEAD".execute().trim()
    val modVersion: String by extraProperty("mod_version")

    version = modVersion + "+" + gitRevision.substring(0, 10)
}

// region Util functions
fun String.execute(envp: Array<String>? = null, dir: File = projectDir): String {
    val process = Runtime.getRuntime().exec(this, envp, dir)
    return process.inputStream.reader().readText()
}

// endregion
