import org.valkyrienskies.extraProperty

plugins {
    id("org.valkyrienskies.maven-repo-conventions")
    id("architectury-plugin")
}

architectury {
    compileOnly()
}



val vsCoreBuild = runCatching { gradle.includedBuild("vs-core") }.getOrNull()

subprojects {
    if (vsCoreBuild != null) {
        configurations.configureEach {
            resolutionStrategy {
                useGlobalDependencySubstitutionRules.set(true)
            }
        }
    }
}

tasks.register("updateVsCore") {
    var versionFile: File? = null
    val gradleProperties = file("gradle.properties")

    if (vsCoreBuild != null) {
        versionFile = File(vsCoreBuild.projectDir, "api-game/build/version.txt")

        inputs.file(versionFile)
        outputs.file(gradleProperties)
        dependsOn(vsCoreBuild.task(":api-game:writeVersion"))

        listOf(":impl", ":api", ":api-game", ":util").forEach {
            dependsOn(vsCoreBuild.task("${it}:publishToMavenLocal"))
        }
    }

    onlyIf {
        versionFile != null
    }

    doLast {
        val vsCoreVersion = versionFile!!.readText()
        val newGradleProperties = gradleProperties.readText().replaceFirst("(?m)^vs_core_version=.*".toRegex(), "vs_core_version=$vsCoreVersion")
        gradleProperties.writeText(newGradleProperties)
    }
}

val customReleaseVersion = extra.properties["CustomReleaseVersion"] as String?

// Determine the version
if (customReleaseVersion != null) {
    // Remove release/ from the version if present
    version = customReleaseVersion.replaceFirst("^release/".toRegex(), "")
} else {
    val gitRevision = execute("git", "rev-parse", "HEAD").trim()
    val modVersion: String by extraProperty("mod_version")

    version = modVersion + "+" + gitRevision.substring(0, 10)
}

// region Util functions
fun execute(vararg args: String) = providers.exec { commandLine(*args) }.standardOutput.asText.get()
// endregion
