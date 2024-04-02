import java.net.URI
import java.text.SimpleDateFormat
import java.util.*

plugins {
    java
    kotlin("jvm") version "1.9.10" apply false
    // Required for NeoGradle
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.7"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

subprojects {
    val mod_id: String by project
    val mod_name: String by project
    val mod_author: String by project
    val minecraft_version: String by project
    val block_external_repositories: Boolean = false
    val vs_maven_url: String? by project
    val vs_maven_username: String? by project
    val vs_maven_password: String? by project

    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "com.github.johnrengelman.shadow")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
        withSourcesJar()
        withJavadocJar()
    }

    tasks.jar {
        from(rootProject.file("LICENSE")) {
            rename { "${it}_$mod_id" }
        }

        manifest {
            attributes(
                    "Specification-Title" to mod_name,
                    "Specification-Vendor" to mod_author,
                    "Specification-Version" to archiveVersion,
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to archiveVersion,
                    "Implementation-Vendor" to mod_author,
                    "Implementation-Timestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(Date()),
                    "Timestamp" to System.currentTimeMillis(),
                    "Built-On-Java" to "${System.getProperty("java.vm.version")} (${System.getProperty("java.vm.vendor")})",
                    "Built-On-Minecraft" to minecraft_version
            )
        }
    }

    repositories {
        mavenCentral()
        maven {
            name = "Valkyrien Skies Internal"
            url = URI(vs_maven_url ?: "https://maven.valkyrienskies.org")
            if (vs_maven_username != null && vs_maven_password != null) {
                credentials {
                    username = vs_maven_username
                    password = vs_maven_password
                }
            }
        }
        if (!block_external_repositories) {
            maven("https://repo.spongepowered.org/repository/maven-public/") { name = "Sponge / Mixin" }
            maven("https://maven.parchmentmc.org") { name = "Parchment Maven" }
            maven("https://cursemaven.com") { name = "Curse Maven" }
            maven("https://maven.terraformersmc.com/releases/") // Mod Menu
            maven("https://thedarkcolour.github.io/KotlinForForge/") {
                name = "Kotlin for Forge"
            }
            maven("https://maven.tterrag.com/") {
                name = "tterrag maven"
            }
            maven("https://api.modrinth.com/maven") // LazyDFU, Suggestion Tweaker
            maven("https://maven.shedaniel.me/") // Cloth Config, REI
            maven("https://mvn.devos.one/snapshots/") // Fabric Create, Porting Lib, Forge Tags, Milk Lib
            maven("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/") // Forge Config API Port
            maven("https://maven.tterrag.com/") // Registrate, Forge Create and Flywheel
            maven("https://maven.cafeteria.dev/releases") // Fake Player API
            maven("https://maven.jamieswhiteshirt.com/libs-release") // Reach Entity Attributes
            maven("https://maven.realrobotix.me/createbigcannons/") { // Create Big Cannons
                content {
                    includeGroup("com.rbasamoyai")
                }
            }
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
    
    tasks.processResources {
        val version: String by project
        val group: String by project
        val forge_version: String by project
        val forge_loader_version_range: String by project
        val forge_version_range: String by project
        val minecraft_version_range: String by project
        val fabric_api_version: String by project
        val fabric_loader_version: String by project
        val mod_id: String by project
        val license: String by project
        val description: String by project
        val credits: String by project
        
        val expandProps = mapOf(
                "version" to version, 
                "group" to group, //Else we target the task's group.
                "minecraft_version" to minecraft_version,
                "forge_version" to forge_version,
                "forge_loader_version_range" to forge_loader_version_range,
                "forge_version_range" to forge_version_range,
                "minecraft_version_range" to minecraft_version_range,
                "fabric_api_version" to fabric_api_version,
                "fabric_loader_version" to fabric_loader_version,
                "mod_name" to mod_name,
                "mod_author" to mod_author,
                "mod_id" to mod_id,
                "license" to license,
                "description" to description,
                "credits" to credits
        )

        filesMatching(listOf("pack.mcmeta", "fabric.mod.json", "META-INF/mods.toml", "*.mixins.json")) {
            expand(expandProps)
        }
        inputs.properties(expandProps)
    }

    // Disables Gradle's custom module metadata from being published to maven. The
    // metadata includes mapped dependencies which are not reasonably consumable by
    // other mod developers.
    tasks.withType<GenerateModuleMetadata> {
        enabled = false
    }
}
