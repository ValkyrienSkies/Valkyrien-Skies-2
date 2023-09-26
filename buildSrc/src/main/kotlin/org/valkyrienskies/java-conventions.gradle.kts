package org.valkyrienskies

import org.gradle.kotlin.dsl.java

plugins {
    java
}

java {
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}
