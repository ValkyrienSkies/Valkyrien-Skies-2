package org.valkyrienskies

plugins {
    id("org.valkyrienskies.minecraft-platform-conventions")
}
private val commonProject: String by extraProperty("common_project")

dependencies {
    val shade by configurations

    modImplementation("net.fabricmc", "fabric-loader", rootProject.fabricLoaderVersion)
    modApi("net.fabricmc.fabric-api", "fabric-api", rootProject.fabricApiVersion)
    shade(project(commonProject, "transformProductionFabric")) { isTransitive = false }
}

architectury {
    platformSetupLoomIde()
    fabric()
}

tasks {
    processResources {
        val version = project.version
        inputs.property("version", version)

        filesMatching("fabric.mod.json") {
            expand("version" to version)
        }
    }
    remapJar {
        injectAccessWidener = true
    }
}
