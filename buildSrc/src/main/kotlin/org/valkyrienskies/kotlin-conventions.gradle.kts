package org.valkyrienskies

import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_7

plugins {
    kotlin("jvm")
}

kotlin {
    compilerOptions {
        apiVersion.set(KOTLIN_1_7)
    }
}
