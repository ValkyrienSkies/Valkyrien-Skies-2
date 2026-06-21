import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.ByteArrayOutputStream
import dev.architectury.plugin.ArchitectPluginExtension

buildscript {
    // Lock plugin dependencies
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    java
    // Needed for Forge+Fabric
    id("architectury-plugin") version "3.4.146"
    id("dev.architectury.loom") version "1.3.355" apply false
    id ("io.github.juuxel.loom-vineflower") version "1.11.0" apply false
    // Kotlin
    id ("org.jetbrains.kotlin.jvm") version "2.0.0" apply false
    id ("com.matthewprenger.cursegradle") version "1.4.0" apply false
    id ("com.modrinth.minotaur") version "2.4.3" apply false
}

fun String.execute(currentWorkingDir: File = file("./")): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        workingDir = currentWorkingDir
        commandLine = this@execute.split("\\s".toRegex())
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

val minecraft_version: String by project
val archives_base_name: String by project

val maven_group: String by project
val mod_version: String by project
val block_external_repositories: String by project
val vs_maven_url: String by project
val vs_maven_username: String by project
val vs_maven_password: String by project


// Determine the version
if (project.hasProperty("CustomReleaseVersion")) {
    // Remove release/ from the version if present
    var version = (project.property("CustomReleaseVersion") as String).replaceFirst("^release/", "")
} else if (!project.hasProperty("CustomVersion")) {
    val gitRevision = "git rev-parse HEAD".execute().trim()

    version = mod_version + "+" + gitRevision.substring(0, 10)
} else {
    version = mod_version + "+" + project.property("CustomVersion")
}

architectury {
    minecraft = minecraft_version
}


// Lock dependencies
// https://docs.gradle.org/current/userguide/dependency_locking.html
dependencyLocking {
    lockAllConfigurations()
}

task("updateVsCore") {
    var versionFile: File? = null
    val gradleProperties = file("gradle.properties")

    try {
        val vsCoreBuild = gradle.includedBuild("vs-core")
        versionFile = File(vsCoreBuild.projectDir, "internal/build/version.txt")

        inputs.file(versionFile)
        outputs.file(gradleProperties)
        dependsOn(vsCoreBuild.task(":internal:writeVersion"))

        listOf(":impl", ":api", ":internal", ":util").forEach { project ->
            dependsOn(vsCoreBuild.task("$project:publishToMavenLocal"))
        }
    } catch (ignore: UnknownDomainObjectException) {}

    onlyIf {
        versionFile != null
    }

    doLast {
        val vsCoreVersion = versionFile!!.readText()
        val newGradleProperties = gradleProperties.readText().replaceFirst("(?m)^vs_core_version=.*", "vs_core_version=" + vsCoreVersion)
        gradleProperties.writeText(newGradleProperties)
    }
}


subprojects {
    apply(plugin = "dev.architectury.loom")
    // Apply checkstyle and ktlint to check the code style of every sub project
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.github.juuxel.loom-vineflower")

    val loom = project.extensions.getByType<LoomGradleExtensionAPI>()
    loom.apply {
        intermediaryUrl =
            "https://repo.spongepowered.org/repository/maven-public/net/fabricmc/intermediary/%1\$s/intermediary-%1\$s-v2.jar"
    }

    try {
        val vsCoreBuild = gradle.includedBuild("vs-core")
        listOf(":impl", ":api", ":internal").forEach {
            tasks.named("compileJava") {
                dependsOn(vsCoreBuild.task("$it:publishToMavenLocal"))
            }
        }
    } catch (_: UnknownDomainObjectException) {
    }

    repositories {
        try {
            val vsCoreBuild = gradle.includedBuild("vs-core")
            mavenLocal {
                content {
                    includeGroup("org.valkyrienskies.core")
                }
            }
        } catch (_: UnknownDomainObjectException) {
        }

        mavenCentral()
        if (block_external_repositories != "true") {
            maven {
                name = "Robotix's Public Maven "
                url = uri("https://maven.realrobotix.me/master/")
            }
            mavenLocal()
            maven {
                name = "ParchmentMC"
                url = uri("https://maven.parchmentmc.org")
            }
            maven {
                name = "Curse Maven"
                url = uri("https://cursemaven.com")
                content {
                    includeGroup("curse.maven")
                }
            }
            maven {
                name = "TerraformersMC Maven"
                url = uri("https://maven.terraformersmc.com/releases/")
                content {
                    includeGroup("com.terraformersmc")
                }
            } // Mod Menu
            maven {
                name = "Kotlin for Forge"
                url = uri("https://thedarkcolour.github.io/KotlinForForge/")
                content {
                    includeGroup("thedarkcolor")
                }
            }
            maven {
                name = "tterrag maven"
                url = uri("https://maven.tterrag.com/")
                content {
                    includeGroup("com.tterrag.registrate")
                    includeGroup("com.tterrag.registrate_fabric")
                }
            }
            maven {
                name = "Mod Maven"
                url = uri("https://modmaven.dev/")
                content {
                    includeGroup("teamtwilight")
                    includeGroup("teamreborn")
                } // Twilight Forest
            }
            maven {
                name = "Modrinth Maven"
                url = uri("https://api.modrinth.com/maven")
                content {
                    includeGroup("maven.modrinth")
                }
            } // LazyDFU, Suggestion Tweaker, Create Big Cannons
            maven {
                name = "Shedaniel's Maven"
                url = uri("https://maven.shedaniel.me/")
                content {
                    includeGroup("me.shedaniel")
                }
            } // Cloth Config, REI
            maven {
                name = "Architectury Maven"
                url = uri("https://maven.architectury.dev/")
                content {
                    includeGroup("dev.architectury")
                }
            }
            maven {
                name = "Create Maven"
                url = uri("https://maven.createmod.net")
                content {
                    includeGroup("com.simibubi.create")
                    includeGroup("net.createmod.ponder")
                    includeGroup("dev.engine-room.flywheel")
                }
            } // Create, Ponder, Flywheel
            maven {
                name = "IThundxrs Maven Repository"
                url = uri("https://maven.ithundxr.dev/mirror")
                content {
                    includeGroup("com.tterrag.registrate")
                }
            } // Registrate
            maven {
                name = "Fuzss's Maven"
                url = uri("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/")
                content {
                    includeGroup("fuzs.forgeconfigapiport")
                }
            } // ForgeConfigAPIPort

            maven {
                name = "devOS release Maven"
                url = uri("https://mvn.devos.one/releases")
                content {
                    includeGroup("io.github.fabricators_of_create.Porting-Lib")
                }
            } // Porting Lib releases
            maven {
                name = "devOS snapshot Maven"
                url = uri("https://mvn.devos.one/snapshots/")
                content {
                    includeGroup("io.github.tropheusj")
                    includeGroup("com.simibubi.create")
                    includeGroup("io.github.fabricators_of_create")
                    includeGroup("com.tterrag.registrate_fabric")
                }
            } // Create and several dependencies
            maven {
                name = "Cafeteria Development"
                url = uri("https://maven.cafeteria.dev/releases")
                content {
                    includeGroup("dev.cafeteria")
                }
            } // Fake Player API
            maven {
                name = "jamieswhiteshirt"
                url = uri("https://maven.jamieswhiteshirt.com/libs-release")
                content {
                    includeGroup("com.jamieswhiteshirt")
                }
            } // Reach Entity Attributes
            maven { // Fabric ASM for Porting Lib
                name = "JitPack"
                url = uri("https://jitpack.io/")
                content {
                    includeGroupAndSubgroups("com.github")
                }
            }
            maven {
                name = "Robotix's Public Maven "
                url = uri("https://maven.realrobotix.me/createbigcannons/")
                content {
                    includeGroup("com.rbasamoyai")
                }
            } // Create Big Cannons
            maven {
                name = "CC-Tweaked"
                url = uri("https://maven.squiddev.cc")
                content {
                    includeGroup("cc.tweaked")
                }
            }
            maven {
                name = "FTB Stuffs"
                url = uri("https://maven.ftb.dev/releases")
                content {
                    includeGroup("dev.ftb.mods")
                }
            }
            maven { // Hexcasting
                name = "BlameJared"
                url = uri("https://maven.blamejared.com")
                content {
                    includeGroup("at.petra-k.hexcasting")
                    includeGroup("at.petra-k.paucal")
                    includeGroup("vazkii.patchouli")
                }
            }
            maven { // Hexal
                name = "Hexxy Media"
                url = uri("https://maven.hexxy.media")
                content {
                    includeGroup("ram.talia.hexal")
                }
            }
        }
        maven {
            name = "Valkyrien Skies Internal"
            url = uri(if (vs_maven_url != "") vs_maven_url else "https://maven.valkyrienskies.org")
            content {
                includeGroup("org.valkyrienskies")
                includeGroup("org.valkyrienskies.core")
                includeGroup("com.fasterxml.jackson.module")
            }
            if ((vs_maven_username != "") && (vs_maven_password != "")) {
                credentials {
                    username = vs_maven_username
                    password = vs_maven_password
                }
            }
        }
    }

    // Remove automatically added repos
    if (block_external_repositories.equals("true")) {
        repositories.removeIf {
            val url = (it as? MavenArtifactRepository)?.url.toString()
            url.contains("maven.minecraftforge.net") ||
                url.contains("maven.fabricmc.net") ||
                url.contains("maven.architectury.dev")
        }
    }

    dependencies {
        "minecraft"("com.mojang:minecraft:${minecraft_version}")
        // The following line declares the mojmap mappings, you may use other mappings as well
        "mappings"(loom.officialMojangMappings())

        compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    }
}


allprojects {
    apply(plugin = "java")
    apply(plugin = "architectury-plugin")
    apply(plugin = "maven-publish")

    // Set the base name, version, and group to the values in the gradle.properties
    base.archivesName.set(archives_base_name)
    group = maven_group
    version = rootProject.version

    extensions.configure<PublishingExtension> {
        repositories {
            repositories {
                if ((vs_maven_username != "") && (vs_maven_password != "")) {
                    println("Publishing to VS Maven ($version)")
                    maven {
                        name = "VSMaven"
                        url = uri(vs_maven_url)
                        credentials {
                            username = vs_maven_username
                            password = vs_maven_password
                        }
                    }
                }
                if (System.getenv("GITHUB_ACTOR") != null) {
                    println("Publishing to Github Packages ($version)")
                    maven {
                        name = "GitHubPackages"
                        url = uri("https://maven.pkg.github.com/ValkyrienSkies/Valkyrien-Skies-2")
                        credentials {
                            username = System.getenv("GITHUB_ACTOR")
                            password = System.getenv("GITHUB_TOKEN")
                        }
                    }
                }
            }
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        kotlinOptions.jvmTarget = "17"
        kotlinOptions.freeCompilerArgs += listOf("-Xjvm-default=all", "-opt-in=org.valkyrienskies.core.api.VsBeta")
    }

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
    }
}
