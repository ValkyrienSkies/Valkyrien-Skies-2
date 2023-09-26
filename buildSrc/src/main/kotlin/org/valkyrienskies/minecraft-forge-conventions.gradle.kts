package org.valkyrienskies

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask

/**
 * This plugin configures a forge project
 */

plugins {
    id("org.valkyrienskies.minecraft-platform-conventions")
}

private val commonProject: String by extraProperty("common_project")

architectury {
    platformSetupLoomIde()
    forge()
}

loom {
    forge {
        convertAccessWideners.set(true)
    }
}

dependencies {
    val forge by configurations
    val shade by configurations

    forge("net.minecraftforge:forge:${rootProject.forgeVersion}")
    shade(project(commonProject, "transformProductionForge")) { isTransitive = false }
}


tasks {
    shadowJar {
        exclude("fabric.mod.json")
    }
    processResources {
        val version = project.version
        inputs.property("version", version)

        filesMatching("META-INF/mods.toml") {
            expand("version" to version)
        }
    }
}
