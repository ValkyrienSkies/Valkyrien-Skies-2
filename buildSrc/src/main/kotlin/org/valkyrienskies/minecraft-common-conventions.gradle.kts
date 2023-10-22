package org.valkyrienskies

plugins {
    id("org.valkyrienskies.minecraft-conventions")
}

dependencies {
    // We depend on fabric loader here to use the fabric @Environment annotations
    // Do NOT use other classes from fabric loader
    modImplementation("net.fabricmc", "fabric-loader", rootProject.fabricLoaderVersion)
}

architectury {
    common("forge", "fabric")
}
