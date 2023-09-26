package org.valkyrienskies

repositories {
    mavenLocal() {
        content {
            includeGroup("org.valkyrienskies.core")
        }
    }
    mavenCentral()
    gradlePluginPortal()

    maven {
        name = "Valkyrien Skies Internal"
        setUrl(vsMavenUrl)

        if (vsMavenUsername != null && vsMavenPassword != null) {
            credentials {
                username = vsMavenUsername
                password = vsMavenPassword
            }
        }
    }
}

afterEvaluate {
    if (blockExternalRepositories) {
        val allowedHosts = setOf(
            "libraries.minecraft.net",
            "repo.maven.apache.org",
            "plugins.gradle.org",
            uri(vsMavenUrl).host
        )

        logger.info("block_external_repositories is enabled, removing " +
            "repositories with hosts not in this list: $allowedHosts")

        repositories.removeIf { repo ->
            val url = (repo as? MavenArtifactRepository)?.url ?: return@removeIf false

            // If this is an external host, check that it's on the allowed list
            // If it's not, return true to remove it
            if (url.host != null && url.scheme != "file" && !allowedHosts.contains(url.host)) {
                logger.info("Removing the repository '${repo.name}' with url '$url'")
                return@removeIf true
            }

            false
        }
    }
}
