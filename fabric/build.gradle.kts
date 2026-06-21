val vs_core_version: String by rootProject
val archives_base_name: String by rootProject

val port_lib_modules: String by project

plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jetbrains.kotlin.jvm")
    id("com.matthewprenger.cursegradle")
    id("com.modrinth.minotaur")
}

apply(from = "../gradle-scripts/publish-curseforge.gradle")

architectury {
    platformSetupLoomIde()
    fabric()
}

configurations {
    create("common")
    create("shadowCommon") // Don't use shadow from the shadow plugin because we don't want IDEA to index this
    named("compileClasspath") {
        extendsFrom(configurations["common"])
    }
    named("runtimeClasspath") {
        extendsFrom(configurations["common"])
    }
    named("developmentFabric") {
        extendsFrom(configurations["common"])
    }
}

loom {
    accessWidenerPath = project(":common").loom.accessWidenerPath
}

dependencies {
    // region Basic dependencies
    include(implementation(annotationProcessor(libs.fabric.mixinExtras.get())!!)!!)

    modImplementation(libs.common.fabricLoader)

    add("common", project(":common", configuration = "namedElements")) {
        isTransitive = false
    }
    add("shadowCommon", project(":common", configuration = "transformProductionFabric")) {
        isTransitive = false
    }

    // Depend on the fabric kotlin mod
    include(modImplementation(libs.fabric.kotlin.get())!!)

    include(modImplementation(libs.fabric.forgeConfigApiPort.get())!!)
    // endregion

    // region Compat dependencies

    modRuntimeOnly(libs.fabric.forgeConfigScreens)

    modCompileOnly(libs.common.createUtilities)
    modCompileOnly(libs.common.sodium)
    // Disable indium until we update sodium to newer versions
    //modRuntimeOnly("maven.modrinth:indium:${indium_version}")
    modCompileOnly(libs.common.iris)

    modRuntimeOnly(libs.fabric.modmenu)


    // Depend on the fabric API
    modImplementation(libs.fabric.fabricApi)

    modCompileOnly(libs.common.teamRebornEnergy) { isTransitive = false }

    // CC Tweaked
    //modRuntimeOnly("cc.tweaked:cc-tweaked-${minecraft_version}-fabric:${cc_tweaked_version}")
    // CC Restitched
    modCompileOnly(libs.fabric.ccTweaked)

    //Very many players
    //modImplementation("curse.maven:vmp-fabric-552542:4754074")

    // EMF compat
    modCompileOnly(libs.fabric.emf)
    modCompileOnly(libs.fabric.etf)

    modCompileOnly("curse.maven:vista-1368607:7929284")

    // Create compat
    //modImplementation("com.simibubi.create:create-fabric:${create_fabric_version}") {
    //    exclude group: "com.github.AlphaMode", module: "fakeconfigtoml"
    //}
    modCompileOnly(libs.common.createFabric)
    modRuntimeOnly(libs.common.createFabric)
    //modImplementation("com.tterrag.registrate_fabric:Registrate:${registrate_version}")

    //modImplementation("io.github.fabricators_of_create.Porting-Lib:Porting-Lib:$port_lib_version")
    val port_lib_version = libs.versions.fabric.portLib.get()
    port_lib_modules.split(",").forEach { module ->
        modCompileOnly("io.github.fabricators_of_create.Porting-Lib:$module:$port_lib_version")
    }
    modCompileOnly("curse.maven:vanillin-965702:6446557")

    modCompileOnly(libs.bundles.fabric.createDeps)

    // Dynmap
    modCompileOnly(libs.common.dynmap)

    // Hexcasting
    modCompileOnly(libs.fabric.hexcasting) { isTransitive = false }

    // HexTweaks
    modCompileOnly(libs.common.hextweaks)

    // Hexical
    // fixme modCompileOnly("miyucomics.hexical:hexical:$hexical_version") { isTransitive = false }

    // Ephemera
    modCompileOnly(libs.common.ephemera)

    // Hexal
    modCompileOnly(libs.fabric.hexal) { isTransitive = false }

    // Immersive portals
    modCompileOnly(libs.bundles.common.immptl) { isTransitive = false }

    // Connectible Chains [Fabric]
    modCompileOnly("curse.maven:connectiblechains-415681:7148381")

    // endregion

    // region vs
    implementation("org.valkyrienskies.core:internal:${vs_core_version}")
    implementation("org.valkyrienskies.core:util:${vs_core_version}")
    runtimeOnly("org.valkyrienskies.core:impl:${vs_core_version}") {
        exclude( module = "netty-buffer")
        exclude( module = "fastutil")
        exclude( module = "kotlin-stdlib-jdk8")
    }
    // endregion

    // region Shade vs-core
    add("shadowCommon", "org.valkyrienskies.core:impl:$vs_core_version") {
        exclude(module = "netty-buffer")
        exclude(module = "fastutil")
        exclude(module = "kotlin-stdlib-jdk8") // Don't shade kotlin-stdlib-jdk8, even though vs-core depends on it
        exclude(group = "com.google.guava")
        exclude(module = "jsonschema.module.addon")
    }
    include(implementation("com.fasterxml:classmate:1.5.1")!!)
    // endregion
}

// Copy the VS common access widener to the generated resources folder
//
// Note: We have to do this because fabric can"t find the access widener unless its in the fabric project
val generatedResourcesDir = file("src/generated/resources")
tasks.register<Copy>("copyAccessWidener") {
    from(project(":common").file("src/main/resources/valkyrienskies-common.accesswidener"))
    into(generatedResourcesDir)
}

// Add [generatedResourcesDir] as a folder to search for resources
sourceSets {
    main {
        resources {
            srcDir(generatedResourcesDir)
        }
    }
}

tasks.processResources {
    dependsOn("copyAccessWidener")
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

tasks.shadowJar {
    configurations = listOf(project.configurations.getByName("shadowCommon"))
    archiveClassifier.set("dev-shadow")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Ignore duplicate valkyrienskies-common.accesswidener files
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*:.*")) // Don"t shade kotlin!
        exclude(dependency("org.apache.commons:commons-lang3:.*")) // Don"t apache-commons, see #617
    }
    // Exclude dummy Optifine classes
    exclude("net/optifine/**")
}

tasks.remapJar {
    dependsOn(tasks.shadowJar)

    input.set(tasks.shadowJar.flatMap { it.archiveFile })

    archiveClassifier.set(null as String?)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Ignore duplicate valkyrienskies-common.accesswidener files
}

tasks.jar {
    archiveClassifier.set("dev")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Ignore duplicate valkyrienskies-common.accesswidener files
}

tasks.sourcesJar {
    dependsOn("copyAccessWidener")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Ignore duplicate valkyrienskies-common.accesswidener files
    val commonSources = project(":common").tasks.named<Jar>("sourcesJar")
    dependsOn(commonSources)
    from(commonSources.flatMap { it.archiveFile }.map { zipTree(it) })
}

components.named<AdhocComponentWithVariants>("java") {
    withVariantsFromConfiguration(configurations.getByName("shadowRuntimeElements")) {
        skip()
    }
}

// Publish to Mavens
publishing {
    publications {
        create<MavenPublication>("mavenFabric") {
            groupId = "org.valkyrienskies"
            version = project.version.toString()
            artifactId = archives_base_name + "-" + project.name
            // Publish the dev shadow jar to maven
            artifact(tasks.shadowJar.flatMap { it.archiveFile }) {
                classifier = "dev-shadow"
            }
            from(components["java"])
        }
    }
}
