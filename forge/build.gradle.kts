plugins {
    idea
    `maven-publish`
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
    id("org.parchmentmc.librarian.forgegradle") version "1.+"
    id("org.spongepowered.mixin") version "0.7-SNAPSHOT"
}

val mod_id: String by project
val minecraft_version: String by project
val forge_version: String by project
val parchment_version: String by project
val vs_core_version: String by project
val create_forge_version: String by project
val flywheel_forge_version: String by project
val registrate_version: String by project

base {
    archivesName.set("${mod_id}-forge-${minecraft_version}")
}

mixin {
    add(sourceSets.main.get(), "$mod_id.refmap.json")
    config("$mod_id.mixins.json")
    config("$mod_id.forge.mixins.json")
}

minecraft {
    mappings("parchment", "$parchment_version-$minecraft_version")

    copyIdeResources = true //Calls processResources when in dev

    // Automatically enable forge AccessTransformers if the file exists
    // This location is hardcoded in Forge and can not be changed.
    // https://github.com/MinecraftForge/MinecraftForge/blob/be1698bb1554f9c8fa2f58e32b9ab70bc4385e60/fmlloader/src/main/java/net/minecraftforge/fml/loading/moddiscovery/ModFile.java#L123
    val transformerFile = file("src/main/resources/META-INF/accesstransformer.cfg")
    if (transformerFile.exists())
        accessTransformer(transformerFile)

    runs {
        create("client") {
            workingDirectory(project.file("run"))
            ideaModule("${rootProject.name}.${project.name}.main")
            taskName("Client")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")
            mods {
                create("modRun") {
                    source(sourceSets.main.get())
                    source(project(":common").sourceSets.main.get())
                }
            }
        }

        create("server") {
            workingDirectory(project.file("run"))
            ideaModule("${rootProject.name}.${project.name}.main")
            taskName("Server")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")
            mods {
                create("modServerRun") {
                    source(sourceSets.main.get())
                    source(project(":common").sourceSets.main.get())
                }
            }
        }

        create("data") {
            workingDirectory(project.file("run"))
            ideaModule("${rootProject.name}.${project.name}.main")
            args(
                "--mod", mod_id,
                "--all",
                "--output", file("src/generated/resources").absolutePath,
                "--existing", file("src/main/resources/").absolutePath
            )
            taskName("Data")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")
            mods {
                create("modDataRun") {
                    source(sourceSets.main.get())
                    source(project(":common").sourceSets.main.get())
                }
            }
        }
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")

dependencies {
    minecraft("net.minecraftforge:forge:${minecraft_version}-${forge_version}")
    minecraftLibrary(project(":common", "namedElements")) { isTransitive = false }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT:processor")


    compileOnly(fg.deobf("curse.maven:rubidium-574856:4024781"))

    // Create compat
    implementation("com.simibubi.create:create-${minecraft_version}:${create_forge_version}:slim") { isTransitive = false }
    implementation("com.jozufozu.flywheel:flywheel-forge-${minecraft_version}:${flywheel_forge_version}")
    implementation("com.tterrag.registrate:Registrate:${registrate_version}")

    // CC Tweaked
    compileOnly(fg.deobf("curse.maven:cc-tweaked-282001:4061947"))

    // TIS-3d
    compileOnly(fg.deobf("curse.maven:tis3d-238603:3738437"))
    //modImplementation("curse.maven:tis3d-238603:3738437")
    //modImplementation("curse.maven:markdownmanual-502485:3738124")

    // Modular Routers
    compileOnly(fg.deobf("curse.maven:mr-250294:3776175"))

    // Mekanism
    compileOnly(fg.deobf("curse.maven:mekanism-268560:3875976"))

    // Add Kotlin for Forge (3.12.0)
    minecraftLibrary("curse.maven:kotlinforforge-351264:4513187")

    // Cloth for config
    api(fg.deobf("me.shedaniel.cloth:cloth-config-forge:6.3.81"))

    // Shade vs-core
    minecraftLibrary("org.valkyrienskies.core:impl:${vs_core_version}") { isTransitive = false }


    // region Manually include every single dependency of vs-core (total meme)
    minecraftLibrary("org.valkyrienskies.core:api:${vs_core_version}") {
        isTransitive = false
    }

    minecraftLibrary("org.valkyrienskies.core:api-game:${vs_core_version}") {
        isTransitive = false
    }

    minecraftLibrary("org.valkyrienskies.core:util:${vs_core_version}") {
        isTransitive = false
    }

    minecraftLibrary("org.valkyrienskies:physics_api_krunch:1.0.0+7db6a103f1") {
        isTransitive = false
    }

    minecraftLibrary("org.valkyrienskies:physics_api:1.0.0+0ba0cc41e1") {
        isTransitive = false
    }

    minecraftLibrary("javax.inject:javax.inject:1") { isTransitive = false }

    // JOML for Math
    minecraftLibrary("org.joml:joml:1.10.4") { isTransitive = false }
    minecraftLibrary("org.joml:joml-primitives:1.10.0") { isTransitive = false }

    // Apache Commons Math for Linear Programming
    minecraftLibrary("org.apache.commons:commons-math3:3.6.1") { isTransitive = false }

    // Jackson Binary Dataformat for Object Serialization
    val jacksonVersion = "2.14.0"
    // forked to remove module-info
    minecraftLibrary("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion-rubyfork") { isTransitive = false }
    minecraftLibrary("com.fasterxml.jackson.module:jackson-module-parameter-names:$jacksonVersion") { isTransitive = false }
    minecraftLibrary("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:$jacksonVersion") { isTransitive = false }
    minecraftLibrary("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion") { isTransitive = false }
    minecraftLibrary("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion") { isTransitive = false }
    minecraftLibrary("com.fasterxml.jackson.core:jackson-core:$jacksonVersion") { isTransitive = false }
    minecraftLibrary("com.github.Rubydesic:jackson-kotlin-dsl:1.2.0") { isTransitive = false }

    minecraftLibrary("com.networknt:json-schema-validator:1.0.71") { isTransitive = false }
    minecraftLibrary("com.ethlo.time:itu:1.7.0") { isTransitive = false }
    minecraftLibrary("com.github.victools:jsonschema-module-jackson:4.25.0") { isTransitive = false }
    minecraftLibrary("com.github.victools:jsonschema-generator:4.25.0") { isTransitive = false }
    minecraftLibrary("com.fasterxml:classmate:1.5.1") { isTransitive = false }
    minecraftLibrary("com.flipkart.zjsonpatch:zjsonpatch:0.4.11") { isTransitive = false }
    minecraftLibrary("org.apache.commons:commons-collections4:4.3") { isTransitive = false }

    minecraftLibrary("com.google.dagger:dagger:2.43.2") { isTransitive = false }

    // endregion
}

tasks {
    withType<JavaCompile> { source(project(":common").sourceSets.main.get().allSource) }

    //javadoc { source(project(":common").sourceSets.main.get().allJava) }

    named("sourcesJar", Jar::class) { from(project(":common").sourceSets.main.get().allSource) }

    processResources { from(project(":common").sourceSets.main.get().resources) }

    jar { finalizedBy("reobfJar") }
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            artifactId = base.archivesName.get()
            artifact(tasks.jar)
            fg.component(this)
        }
    }

    repositories {
        maven("file://${System.getenv("local_maven")}")
    }
}

sourceSets.forEach {
    val dir = layout.buildDirectory.dir("sourceSets/${it.name}")
    it.output.setResourcesDir(dir)
    it.java.destinationDirectory.set(dir)
}
