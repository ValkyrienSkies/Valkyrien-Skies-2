plugins {
    `kotlin-dsl`
}

val vsMavenUsername = extra.properties["vs_maven_username"] as String?
val vsMavenPassword  = extra.properties["vs_maven_password"] as String?
val vsMavenUrl = (extra.properties["vs_maven_url"] as String?) ?: "https://maven.valkyrienskies.org"

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
}

dependencies {
    implementation("org.jetbrains.kotlin", "kotlin-gradle-plugin", "1.9.10")
    implementation("architectury-plugin", "architectury-plugin.gradle.plugin", "3.4.146")
    implementation("dev.architectury.loom", "dev.architectury.loom.gradle.plugin", "1.3.355")
    implementation("io.github.juuxel.loom-vineflower", "io.github.juuxel.loom-vineflower.gradle.plugin", "1.11.0")
    implementation("com.github.johnrengelman", "shadow", "8.1.1")
}
