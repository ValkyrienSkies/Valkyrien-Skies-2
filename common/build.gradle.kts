
val minecraft_version: String by rootProject
val vs_core_version: String by rootProject
val enabled_platforms: String by rootProject
val archives_base_name: String by rootProject

val alexscaves_version: String by project

dependencies {
    // region Basic dependencies
    implementation(annotationProcessor(libs.common.mixinExtras.get())!!)
    testImplementation("junit:junit:4.13.2")

    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("com.google.guava:guava:31.1-jre")

    // We depend on fabric loader here to use the fabric @Environment annotations
    // Do NOT use other classes from fabric loader
    modImplementation(libs.common.fabricLoader)

    modCompileOnly(libs.common.forgeConfigApiPort) { isTransitive = false }
    // endregion

    // region Compat dependencies

    modCompileOnly(libs.common.sodium)
    modCompileOnly(libs.common.iris)

    // Alex Caves
    modCompileOnly("maven.modrinth:alexs-caves:$alexscaves_version")

    // FTB Stuffs
    //modCompileOnly("dev.ftb.mods:ftb-chunks:2001.3.6") { transitive = false }

    // Weather2 1.20.1
    modCompileOnly("curse.maven:weather-storms-tornadoes-237746:5244118")

    // TIS-3d
    modCompileOnly(libs.common.tis3d)

    // CC-Tweaked
    modCompileOnly(libs.common.ccTweaked)

    // Dynmap
    modCompileOnly(libs.common.dynmap)

    // Hexcasting
    modCompileOnly(libs.common.hexcasting) { isTransitive = false }

    // HexTweaks
    modCompileOnly(libs.common.hextweaks)

    // Ephemera
    modCompileOnly(libs.common.ephemera)

    // Hexal
    modCompileOnly(libs.common.hexal) { isTransitive = false }

    // Supplementaries (Moonlight Lib)
    modCompileOnly(libs.common.moonlight)

    // EMF compat
    modCompileOnly(libs.common.emf)
    modCompileOnly(libs.common.etf)

    modCompileOnly("curse.maven:vista-1368607:7929284")

    // Weather2 1.20.1
    modCompileOnly("curse.maven:weather-storms-tornadoes-237746:5244118")

    //Common create compat,
    //We just use a version from a platform and hope the classes exist on both versions and mixins apply correctly
    modCompileOnly(libs.common.createFabric)
        { exclude( group = "com.github.AlphaMode", module = "fakeconfigtoml") }
    modCompileOnly(libs.fabric.fabricApi) { isTransitive = false }
    modCompileOnly("curse.maven:vanillin-965702:6446557")

    modCompileOnly(libs.common.createUtilities)
    modCompileOnly(libs.common.teamRebornEnergy) { isTransitive = false }
    // modCompileOnly("io.github.fabricators_of_create:Porting-Lib:${port_lib_version}+${minecraft_version}")

    //Very many players
    modCompileOnly("curse.maven:vmp-fabric-552542:4754074")

    //Bluemap fabric 1.20.1
    modCompileOnly("curse.maven:bluemap-406463:5555756")

    // Immersive portals
    modCompileOnly(libs.bundles.common.immptl) { isTransitive = false }

    val cbcVersion = libs.versions.common.createbigcannons.version.get()
    val cbcBuild = libs.versions.common.createbigcannons.build.get()
    val rplVersion = libs.versions.common.rpl.get()
    modCompileOnly("com.rbasamoyai:createbigcannons:$cbcVersion+mc.$minecraft_version-fabric$cbcBuild") { isTransitive = false }
    modCompileOnly("com.rbasamoyai:createbigcannons:$cbcVersion+mc.$minecraft_version-forge$cbcBuild") { isTransitive = false }
    modCompileOnly("com.rbasamoyai:ritchiesprojectilelib:$rplVersion+mc.$minecraft_version-forge") { isTransitive = false }

    modCompileOnly("maven.modrinth:theatrical:1.0.0-alpha.28.120+mc1.20.1")

    //endregion ----------

    // region vs-core
    runtimeOnly("org.valkyrienskies.core:impl:${vs_core_version}") {
        exclude(module = "netty-buffer")
        exclude(module = "fastutil")
    }
    implementation("org.valkyrienskies.core:api:${vs_core_version}")
    implementation("org.valkyrienskies.core:internal:${vs_core_version}")
    implementation("org.valkyrienskies.core:util:${vs_core_version}")
    // endregion

    // region Unit testing
    val kotestVersion = "5.4.1"
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("io.kotest:kotest-runner-junit5:${kotestVersion}")
    testImplementation("io.kotest:kotest-property:${kotestVersion}")
    testImplementation("io.kotest:kotest-assertions-core:${kotestVersion}")
    testImplementation("io.mockk:mockk:1.12.5")
    // endregion
}

architectury {
    common(enabled_platforms.split(","))
}

loom {
    accessWidenerPath = file("src/main/resources/valkyrienskies-common.accesswidener")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    // Exclude dummy Optifine classes
    exclude("net/optifine/**")
}

tasks.compileKotlin {
    doLast {
        val dir = destinationDirectory.get().asFile

        val from = File(dir, "META-INF/valkyrienskies-120.kotlin_module")
        val to = File(dir, "META-INF/valkyrienskies-120_common.kotlin_module")

        from.renameTo(to)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenCommon") {
            groupId = "org.valkyrienskies"
            version = project.version.toString()
            artifactId = "${archives_base_name}-${project.name}"

            from(components["java"])

            artifact(tasks.jar) {
                classifier = "dev"
            }
        }
    }
}
