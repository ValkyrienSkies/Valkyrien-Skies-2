package org.valkyrienskies

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.internal.impldep.org.bouncycastle.asn1.x500.style.RFC4519Style.c
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.utils.extendsFrom
import java.util.Locale

/**
 * This plugin configures a "platform" project (i.e., forge, fabric, or quilt)
 */


plugins {
    id("org.valkyrienskies.minecraft-conventions")
    id("com.github.johnrengelman.shadow")
}

private val commonProject: String = property("common_project") as String

// Set the version to the same as the common project
version = project(commonProject).version

// not sure why we don't just use `implementation` for this, but this is what
// the architectury template does
val common by configurations.creating {
    configurations.runtimeClasspath.get().extendsFrom(this)
    configurations.compileClasspath.get().extendsFrom(this)
}

dependencies {
    common(project(commonProject, "namedElements")) { isTransitive = false }
}

loom {
    accessWidenerPath.value(provider { project(commonProject).loom.accessWidenerPath.orNull })

    // https://docs.architectury.dev/plugin/compile_only
    mods {
        maybeCreate("main").apply {
            sourceSet(project.sourceSets.main.get())
            sourceSet(project(commonProject).sourceSets.main.get())
        }
    }
}

// Create a "shade" configuration for shading dependencies
val shade by configurations.creating

tasks {
    // configure the sources jar to include sources from the common project
    named<Jar>("sourcesJar") {
        val commonSources = project(commonProject).tasks.getByName<Jar>("sourcesJar")
        dependsOn(commonSources)

        from(commonSources.archiveFile.map { zipTree(it) })
    }

    shadowJar {
        // configure shadowJar to exclude architectury json files
        exclude("architectury.common.json")

        // use the shade configuration
        configurations = listOf(shade)

        // end in -dev.jar
        archiveClassifier.set("dev")
    }

    // configure remapJar to remap the shaded jar file
    remapJar {
        dependsOn(shadowJar)

        inputFile.set(shadowJar.get().archiveFile)
        archiveClassifier.set("")
    }
}

// don't publish the shadowRuntimeElements configuration added by the shadow plugin
components.named<AdhocComponentWithVariants>("java") {
    withVariantsFromConfiguration(configurations.getByName("shadowRuntimeElements")) {
        skip()
    }
}
