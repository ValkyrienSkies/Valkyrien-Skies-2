import org.valkyrienskies.extraProperty
import org.valkyrienskies.vsCoreApi
import org.valkyrienskies.vsCoreApiGame
import org.valkyrienskies.vsCoreImpl
import org.valkyrienskies.vsCoreUtil

plugins {
    id("org.valkyrienskies.minecraft-forge-conventions")
}

loom {
    forge {
        mixinConfig(
            "valkyrienskies-common.mixins.json",
            "valkyrienskies-forge.mixins.json"
        )
    }

}

dependencies {
    val shade by configurations

    implementation(kotlin("stdlib"))


    compileOnly(vsCoreImpl)
    shade(vsCoreImpl) { isTransitive = false }
    shade(vsCoreApiGame) { isTransitive = false }
    shade(vsCoreApi) { isTransitive = false }
    shade(vsCoreUtil) { isTransitive = false }

    forgeRuntimeLibrary(shade("org.valkyrienskies:physics_api_krunch:1.0.0+14214f4ff8") {
        isTransitive = false
    })

    forgeRuntimeLibrary(shade("org.valkyrienskies:physics_api:1.0.0+216620077e") {
        isTransitive = false
    })

    val mixinExtras = create("com.github.LlamaLad7", "MixinExtras", "0.1.1")

    include(mixinExtras)
    forgeRuntimeLibrary(mixinExtras)
    annotationProcessor(mixinExtras)
    implementation(mixinExtras)

    implementation(project(":api:forge"))
    include(project(":api:forge"))


    val minecraftVersion: String by extraProperty("minecraft_version")
    val createVersion: String by extraProperty("create_version")
    val flywheelVersion: String by extraProperty("flywheel_version")
    val registrateVersion: String by extraProperty("registrate_version")

    modCompileOnly("curse.maven", "rubidium-574856", "4024781")

    // Create compat
    modCompileOnly("com.simibubi.create", "create-${minecraftVersion}", createVersion, classifier = "slim") { isTransitive = false }
    modCompileOnly("com.jozufozu.flywheel", "flywheel-forge-${minecraftVersion}", flywheelVersion)
    modCompileOnly("com.tterrag.registrate", "Registrate", registrateVersion)

    // CC Tweaked
    modCompileOnly("curse.maven", "cc-tweaked-282001", "4061947")

    // TIS-3d
    modCompileOnly("curse.maven", "tis3d-238603", "3738437")
    //modImplementation("curse.maven:tis3d-238603:3738437")
    //modImplementation("curse.maven:markdownmanual-502485:3738124")

    // Add Kotlin for Forge.
    forgeRuntimeLibrary("curse.maven", "kotlinforforge-351264", "3925887")

    include(modImplementation("me.shedaniel.cloth", "cloth-config-forge", "6.3.81"))

    // Cloth for config
    modCompileOnly("me.shedaniel.cloth", "cloth-config-forge", "6.3.81")


    forgeRuntimeLibrary(include("javax.inject:javax.inject:1") { isTransitive = false })

    // JOML for Math
    forgeRuntimeLibrary(include("org.joml:joml:1.10.4") { isTransitive = false })
    forgeRuntimeLibrary(include("org.joml:joml-primitives:1.10.0") { isTransitive = false })

    // Apache Commons Math for Linear Programming
    forgeRuntimeLibrary(include("org.apache.commons:commons-math3:3.6.1") { isTransitive = false })

    val jacksonVersion = "2.14.0"
    // forked to remove module-info
    forgeRuntimeLibrary(include("com.fasterxml.jackson.module", "jackson-module-kotlin", "$jacksonVersion-rubyfork") { isTransitive = false } )
    forgeRuntimeLibrary(include("com.fasterxml.jackson.module", "jackson-module-parameter-names", jacksonVersion) { isTransitive = false })
    forgeRuntimeLibrary(include("com.fasterxml.jackson.dataformat", "jackson-dataformat-cbor", jacksonVersion) { isTransitive = false })
    forgeRuntimeLibrary(include("com.fasterxml.jackson.core", "jackson-databind", jacksonVersion) { isTransitive = false })
    forgeRuntimeLibrary(include("com.fasterxml.jackson.core", "jackson-annotations", jacksonVersion) { isTransitive = false })
    forgeRuntimeLibrary(include("com.fasterxml.jackson.core", "jackson-core", jacksonVersion) { isTransitive = false })
    forgeRuntimeLibrary(include("com.github.Rubydesic", "jackson-kotlin-dsl", "1.2.0") { isTransitive = false })

    forgeRuntimeLibrary(include("com.networknt", "json-schema-validator", "1.0.71") { isTransitive = false})
    forgeRuntimeLibrary(include("com.ethlo.time", "itu", "1.7.0") { isTransitive = false})
    // forgeRuntimeLibrary(include("com.github.imifou", "jsonschema-module-addon", "1.2.1") { isTransitive = false })
    forgeRuntimeLibrary(include("com.github.victools", "jsonschema-module-jackson", "4.25.0") { isTransitive = false })
    forgeRuntimeLibrary(include("com.github.victools", "jsonschema-generator", "4.25.0") { isTransitive = false })
    forgeRuntimeLibrary(include("com.fasterxml", "classmate", "1.5.1") { isTransitive = false })
    forgeRuntimeLibrary(include("com.flipkart.zjsonpatch", "zjsonpatch", "0.4.11") { isTransitive = false })
    forgeRuntimeLibrary(include("org.apache.commons", "commons-collections4", "4.3") { isTransitive = false })

    forgeRuntimeLibrary(include("com.google.dagger", "dagger", "2.43.2") { isTransitive = false})

    forgeRuntimeLibrary(vsCoreImpl) {
        isTransitive = false
        // exclude(group = "org.jetbrains.kotlin")
        // exclude(group = "org.jetbrains.kotlinx")
        // exclude(module = "jackson-module-kotlin")
    }
    forgeRuntimeLibrary(vsCoreApiGame) { isTransitive = false }
    forgeRuntimeLibrary(vsCoreUtil) { isTransitive = false }
    forgeRuntimeLibrary(vsCoreApi) { isTransitive = false }

}



