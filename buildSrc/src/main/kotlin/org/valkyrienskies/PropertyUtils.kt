package org.valkyrienskies

import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.extra

fun Project.vsCoreModule(name: String) = dependencies.create("org.valkyrienskies.core", name, vsCoreVersion)
val Project.vsCoreImpl get() = vsCoreModule("impl")
val Project.vsCoreApi get() = vsCoreModule("api")
val Project.vsCoreApiGame get() = vsCoreModule("api-game")
val Project.vsCoreUtil get() = vsCoreModule("util")

val Project.vsCoreVersion get() = extra["vs_core_version"] as String
val Project.forgeVersion get() = extra["forge_version"] as String
val Project.fabricLoaderVersion get() = extra["fabric_loader_version"] as String
val Project.fabricApiVersion get() = extra["fabric_api_version"] as String
val Project.modId get() = extra["mod_id"] as String
val Project.minecraftVersion get() = extra["minecraft_version"] as String
val Project.vsMavenUsername get() = extra.properties["vs_maven_username"] as String?
val Project.vsMavenPassword get() = extra.properties["vs_maven_password"] as String?
val Project.vsMavenUrl get() = (extra.properties["vs_maven_url"] as String?) ?: "https://maven.valkyrienskies.org"
val Project.blockExternalRepositories get() = (extra.properties["block_external_repositories"] as? String).isNullOrBlank()

