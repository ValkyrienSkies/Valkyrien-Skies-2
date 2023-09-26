import org.valkyrienskies.vsCoreApi

plugins {
    id("org.valkyrienskies.minecraft-common-conventions")
    id("org.valkyrienskies.kotlin-conventions")
}

dependencies {
    api(vsCoreApi)
}

version = "1.0.0"

kotlin {
    // we don't want to depend on the kotlin stdlib, need to disable
    // all the automatically inserted null checks
    // https://github.com/JetBrains/kotlin/blob/master/compiler/cli/cli-common/src/org/jetbrains/kotlin/cli/common/arguments/K2JVMCompilerArguments.kt
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
            "-Xno-param-assertions"
        )
    }
}
