package org.valkyrienskies

plugins {
    id("org.valkyrienskies.maven-repo-conventions")
    id("org.valkyrienskies.kotlin-conventions")
    id("org.valkyrienskies.java-conventions")
    id("dev.architectury.loom")
    id("architectury-plugin")
}

dependencies {
    minecraft("com.mojang", "minecraft", minecraftVersion)
    mappings(loom.officialMojangMappings())
}

architectury {
    minecraft = minecraftVersion

    // https://docs.architectury.dev/plugin/compile_only
    compileOnly()
}
