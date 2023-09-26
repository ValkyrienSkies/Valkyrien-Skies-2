import java.nio.file.Files
import kotlin.io.path.absolute
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

pluginManagement {
    val vsMavenUsername = extra["vs_maven_username"] as String?
    val vsMavenPassword  = extra["vs_maven_password"] as String?
    val vsMavenUrl = (extra["vs_maven_url"] as String?) ?: "https://maven.valkyrienskies.org"
    val blockExternalRepositories = (extra["block_external_repositories"] as? String).isNullOrBlank()

    repositories {
        mavenCentral()
        gradlePluginPortal()

        maven {
            name = "Valkyrien Skies Internal"
            setUrl(vsMavenUrl)

            if (vsMavenUsername != null && vsMavenPassword != null) {
                credentials {
                    username = vsMavenUsername
                    password = vsMavenPassword
                }
            }
        }

        if (!blockExternalRepositories) {
            maven { setUrl("https://maven.architectury.dev/") }
            maven { setUrl("https://maven.fabricmc.net/") }
        }
    }
}

include(
    "api:common",
    "api:fabric",
    "api:forge",
    "base:common",
    "base:fabric",
    "base:forge"
)

rootProject.name = "valkyrienskies"
