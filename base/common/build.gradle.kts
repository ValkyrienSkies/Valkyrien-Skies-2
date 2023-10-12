import org.valkyrienskies.minecraftVersion
import org.valkyrienskies.vsCoreApiGame
import org.valkyrienskies.vsCoreImpl
import org.valkyrienskies.vsCoreUtil
import org.valkyrienskies.vsCoreVersion

plugins {
    id("org.valkyrienskies.minecraft-common-conventions")
}

version = rootProject.version

dependencies {
    implementation(kotlin("stdlib"))
    annotationProcessor(implementation("com.github.LlamaLad7", "MixinExtras", "0.1.1"))

    modApi("me.shedaniel.cloth", "cloth-config", "4.14.64")

    modCompileOnly("curse.maven", "sodium-394468", "3669187")

    // vs-core
    implementation(vsCoreImpl)
    implementation(vsCoreApiGame)

    // implementation(project(path = ":api-common", configuration = "namedElements")) {
    //     isTransitive = false
    // }

    implementation(vsCoreUtil)

    // FTB Stuffs
    modCompileOnly("curse.maven", "ftb-util-404465", "4210935")
    modCompileOnly("curse.maven", "ftb-teams-404468", "4229138")
    modCompileOnly("curse.maven", "ftb-chunks-314906", "4229120")

    // Common create compat,
    // We just use a version from a platform and hope the classes exist on both versions and mixins apply correctly
    val createFabricVersion = project.extra["create_fabric_version"] as String
    val portLibVersion = project.extra["port_lib_version"] as String
    modCompileOnly("com.simibubi.create", "create-fabric-${minecraftVersion}", createFabricVersion) {
        exclude(group = "com.github.AlphaMode", module = "fakeconfigtoml")
    }

    modCompileOnly("com.jozufozu.flywheel", "flywheel-fabric-${minecraftVersion}", "0.6.8-33")
    modCompileOnly("io.github.fabricators_of_create", "Porting-Lib", "${portLibVersion}+${minecraftVersion}")
}

loom {
    accessWidenerPath = file("src/main/resources/valkyrienskies-common.accesswidener")
}
