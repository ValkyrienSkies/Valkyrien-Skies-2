import net.fabricmc.loom.task.RemapJarTask
import org.codehaus.groovy.runtime.DefaultGroovyMethods.mixin

plugins {
    idea
    java
    `maven-publish`
    id("fabric-loom") version("1.4-SNAPSHOT")
}

val mod_id: String by project
val minecraft_version: String by project
val parchment_version: String by project
val vs_core_version: String by project
val create_fabric_version: String by project
val flywheel_fabric_version: String by project
val port_lib_version: String by project
val createbigcannons_version: String by project
val fabric_api_version: String by project

base {
    archivesName = "${mod_id}-common-${minecraft_version}"
}

dependencies {
    minecraft("com.mojang:minecraft:${minecraft_version}")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${minecraft_version}:${parchment_version}@zip")
    })

    annotationProcessor(implementation("com.github.LlamaLad7:MixinExtras:0.1.1")!!)

    compileOnly("org.spongepowered:mixin:0.8.5")
    implementation("com.google.code.findbugs:jsr305:3.0.1")

    modApi("me.shedaniel.cloth:cloth-config:4.14.64")

    modCompileOnly("curse.maven:sodium-394468:3669187")

    // vs-core
    compileOnly("org.valkyrienskies.core:impl:${vs_core_version}") {
        exclude("netty-buffer")
        exclude("fastutil")
    }
    compileOnly("org.valkyrienskies.core:util:${vs_core_version}")

    // FTB Stuffs
    modCompileOnly("curse.maven:ftb-util-404465:4210935")
    modCompileOnly("curse.maven:ftb-teams-404468:4229138")
    modCompileOnly("curse.maven:ftb-chunks-314906:4229120")

    //Common create compat,
    //We just use a version from a platform and hope the classes exist on both versions and mixins apply correctly
    modCompileOnly("com.simibubi.create:create-fabric-${minecraft_version}:${create_fabric_version}") {
        exclude("com.github.AlphaMode", "fakeconfigtoml")
    }
    modCompileOnly("net.fabricmc.fabric-api:fabric-api:${fabric_api_version}")
    modCompileOnly("com.jozufozu.flywheel:flywheel-fabric-${minecraft_version}:${flywheel_fabric_version}")
    modCompileOnly("io.github.fabricators_of_create:Porting-Lib:${port_lib_version}+${minecraft_version}")
    modCompileOnly("com.rbasamoyai:createbigcannons-fabric-${minecraft_version}:${createbigcannons_version}")

    //Bluemap fabric 1.18
    modCompileOnly("curse.maven:bluemap-406463:4950063")
}

loom {
    accessWidenerPath.set(File("src/main/resources/valkyrienskies-common.accesswidener"))
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
