import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    idea
    `maven-publish`
    id("fabric-loom") version("1.4-SNAPSHOT")
}

val mod_id: String by project
val mod_name: String by project
val minecraft_version: String by project
val fabric_api_version: String by project
val fabric_loader_version: String by project
val parchment_version: String by project
val vs_core_version: String by project
val create_fabric_version: String by project
val flywheel_fabric_version: String by project
val port_lib_version: String by project
val createbigcannons_version: String by project
val registrate_version: String by project
val forge_tags_version: String by project
val forge_config_api_port_version: String by project
val reach_entity_attributes_version: String by project
val fake_player_api_version: String by project
val milk_lib_version: String by project
val mixin_extras_version: String by project

val commonMain = project(":common").sourceSets.main.get()
val shade by configurations.creating

base {
    archivesName.set("${mod_id}-fabric-${minecraft_version}")
}

dependencies {
    minecraft("com.mojang:minecraft:${minecraft_version}")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${minecraft_version}:${parchment_version}@zip")
    })
    annotationProcessor(implementation("io.github.llamalad7:mixinextras-fabric:$mixin_extras_version") {})
    modImplementation("net.fabricmc:fabric-loader:$fabric_loader_version")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_api_version")
    implementation("com.google.code.findbugs:jsr305:3.0.1")
    implementation(project(":common", "namedElements"))

    // Depend on the fabric kotlin mod
    include(modImplementation("net.fabricmc:fabric-language-kotlin:1.10.10+kotlin.1.9.10")!!)
    include(modImplementation("me.shedaniel.cloth:cloth-config-fabric:6.3.81")!!)

    modImplementation("curse.maven:sodium-394468:3669187")
    modImplementation("com.terraformersmc:modmenu:3.2.3")

    // Depend on the fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabric_api_version}")

    shade("org.valkyrienskies.core:impl:${vs_core_version}")
    implementation("org.valkyrienskies.core:impl:${vs_core_version}")

        // FTB Stuffs
    modImplementation("curse.maven:ftb-util-404465:4210935")
    modImplementation("curse.maven:ftb-teams-404468:4229138")
    modImplementation("curse.maven:ftb-chunks-314906:4229120")

    // CC Restitched
    modImplementation("curse.maven:cc-restitched-462672:3838648")

    // Create compat
    modImplementation("com.simibubi.create:create-fabric-${minecraft_version}:${create_fabric_version}") {
        exclude("com.github.AlphaMode", "fakeconfigtoml")
    }
    modImplementation("com.jozufozu.flywheel:flywheel-fabric-${minecraft_version}:${flywheel_fabric_version}")
    modImplementation("com.tterrag.registrate_fabric:Registrate:${registrate_version}")
    modImplementation("io.github.fabricators_of_create:Porting-Lib:${port_lib_version}+${minecraft_version}")
    modImplementation("me.alphamode:ForgeTags:${forge_tags_version}")
    modImplementation("net.minecraftforge:forgeconfigapiport-fabric:${forge_config_api_port_version}")
    modImplementation("com.jamieswhiteshirt:reach-entity-attributes:${reach_entity_attributes_version}")
    modImplementation("dev.cafeteria:fake-player-api:${fake_player_api_version}")
    modImplementation("io.github.tropheusj:milk-lib:${milk_lib_version}")
    compileOnly("com.rbasamoyai:createbigcannons-fabric-${minecraft_version}:${createbigcannons_version}")

    //Bluemap fabric 1.18
    modCompileOnly("curse.maven:bluemap-406463:4950063")
}

loom {
    accessWidenerPath.set(project(":common").file("src/main/resources/valkyrienskies-common.accesswidener"))

    mixin {
        add(sourceSets.main.get(), "$mod_id.refmap.json")
    }

    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("run")
        }
        named("server") {
            server()
            configName = "Fabric Server"
            ideConfigGenerated(true)
            runDir("run")
        }
    }
}

tasks {
    named<JavaCompile>("compileJava") {
        source(commonMain.allSource)
    }

    named<KotlinCompile>("compileKotlin") {
        source(commonMain.allSource)
    }

    //javadoc { source(project(":common").sourceSets.main.get().allJava) }

    named<Jar>("sourcesJar") {
        from(commonMain.allSource)
    }

    processResources {
        from(commonMain.allSource)
    }

    named<Jar>("jar") {
        archiveClassifier = "platform"
    }

    named<ShadowJar>("shadowJar") {
        archiveClassifier = "shadow"
        configurations = listOf(shade)
        dependencies {
            exclude(dependency("org.jetbrains.kotlin:.*:.*"))
            exclude(dependency("io.netty:.*:.*"))
            exclude(dependency("it.unimi.dsi:fastutil:.*"))
        }
    }

    named("assemble") {
        dependsOn("shadowJar")
    }

    named<RemapJarTask>("remapJar") {
        dependsOn("shadowJar")
        input.set(shadowJar.get().archiveFile)
    }
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }

    repositories {
        maven("file://${System.getenv("local_maven")}")
    }
}
