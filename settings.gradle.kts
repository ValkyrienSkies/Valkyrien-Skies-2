
pluginManagement {
    val vs_maven_url: String = providers.gradleProperty("vs_maven_url").get()
    val vs_maven_username: String = providers.gradleProperty("vs_maven_username").get()
    val vs_maven_password: String = providers.gradleProperty("vs_maven_password").get()
    val block_external_repositories: String = providers.gradleProperty("block_external_repositories").get()


    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "Valkyrien Skies Internal"

            url = uri(if (vs_maven_url != "") vs_maven_url else "https://maven.valkyrienskies.org")

            if ((vs_maven_username != "") && (vs_maven_password != "")) {
                credentials {
                    username = vs_maven_username
                    password = vs_maven_password
                }
            }
        }
        if (block_external_repositories != "true") {
            maven { url = uri("https://maven.architectury.dev/") }
            maven { url = uri("https://maven.fabricmc.net/") }
        }
    }
}

dependencyResolutionManagement {

    val vs_maven_url: String = providers.gradleProperty("vs_maven_url").get()
    val vs_maven_username: String = providers.gradleProperty("vs_maven_username").get()
    val vs_maven_password: String = providers.gradleProperty("vs_maven_password").get()

    repositories {
        mavenCentral()
        maven {
            name = "Valkyrien Skies Internal"

            url = uri(if (vs_maven_url != "") vs_maven_url else "https://maven.valkyrienskies.org")

            if ((vs_maven_username != "") && (vs_maven_password != "")) {
                credentials {
                    username = vs_maven_url
                    password = vs_maven_username
                }
            }
        }
    }
}

include("common")
include("fabric")
include("forge")

/*
try {
    def candidatePaths = [
        providers.gradleProperty("vsCoreDir").orNull,
        System.getenv("VS_CORE_DIR"),
        "./vs-core",
        "../vs-core",
    ].findAll { it != null && !it.isBlank() }

    def core = candidatePaths
        .collect { file(it) }
        .find { it.isDirectory() }

    if (core == null) {
        def parentDir = rootDir.parentFile
        if (parentDir != null) {
            core = parentDir.listFiles()?.find { it.isDirectory() && it.name == "vs-core" }
        }
    }

    println "VS-CORE DEBUG: Candidate paths: ${candidatePaths}"
    println "VS-CORE DEBUG: Resolved path: ${core?.absolutePath ?: "<not found>"}"
    println "VS-CORE DEBUG: Exists? ${core?.isDirectory() ?: false}"
    if (core?.isDirectory()) {
        includeBuild(core) {
            dependencySubstitution {
                // Tell Gradle: "Whenever someone asks for "org.valkyrienskies.core:api",
                // use the project named ":api" inside this included build instead."
                substitute module("org.valkyrienskies.core:api") using project(":api")
                substitute module("org.valkyrienskies.core:internal") using project(":internal")
                substitute module("org.valkyrienskies.core:util") using project(":util")
                substitute module("org.valkyrienskies.core:impl") using project(":impl")
            }
        }
    }
} catch (SecurityException ignore) {}
*/

rootProject.name = "valkyrienskies"

