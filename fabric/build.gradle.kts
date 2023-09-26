import org.valkyrienskies.extraProperty
import org.valkyrienskies.vsCoreImpl

plugins {
    id("org.valkyrienskies.minecraft-fabric-conventions")
}

dependencies {
    val mixinExtras = create("com.github.LlamaLad7", "MixinExtras", "0.1.1")

    include(mixinExtras)
    annotationProcessor(mixinExtras)
    implementation(mixinExtras)

    include(modImplementation("net.fabricmc", "fabric-language-kotlin", "1.10.10+kotlin.1.9.10"))
    include(modImplementation("me.shedaniel.cloth", "cloth-config-fabric", "6.3.81"))

    modImplementation("curse.maven", "sodium-394468", "3669187")
    modImplementation("com.terraformersmc", "modmenu", "3.2.3")


    // CC Restitched
    modImplementation("curse.maven", "cc-restitched-462672", "3838648")


    val minecraftVersion: String by extraProperty("minecraft_version")
    val createVersion: String by extraProperty("create_version")
    val flywheelVersion: String by extraProperty("flywheel_version")
    val registrateVersion: String by extraProperty("registrate_version")
    val portLibVersion: String by extraProperty("port_lib_version")
    val forgeTagsVersion: String by extraProperty("forge_tags_version")
    val forgeConfigApiPortVersion: String by extraProperty("forge_config_api_port_version")
    val reachEntityAttributesVersion: String by extraProperty("reach_entity_attributes_version")
    val fakePlayerApiVersion: String by extraProperty("fake_player_api_version")
    val milkLibVersion: String by extraProperty("milk_lib_version")

    // Create compat
    modImplementation("com.simibubi.create", "create-fabric-${minecraftVersion}", "${createVersion}+${minecraftVersion}") { isTransitive = false }
    modImplementation("com.jozufozu.flywheel", "flywheel-fabric-${minecraftVersion}", flywheelVersion)
    modImplementation("com.tterrag.registrate_fabric", "Registrate", registrateVersion)
    modImplementation("io.github.fabricators_of_create", "Porting-Lib", "${portLibVersion}+${minecraftVersion}")
    modImplementation("me.alphamode", "ForgeTags", forgeTagsVersion)
    modImplementation("net.minecraftforge", "forgeconfigapiport-fabric", forgeConfigApiPortVersion)
    modImplementation("com.jamieswhiteshirt", "reach-entity-attributes", reachEntityAttributesVersion)
    modImplementation("dev.cafeteria", "fake-player-api", fakePlayerApiVersion)
    modImplementation("io.github.tropheusj", "milk-lib", milkLibVersion)
    
    implementation(project(":api-fabric", "namedElements")) { isTransitive = false }
    include(project(":api-fabric"))

    implementation(vsCoreImpl)
    shade(vsCoreImpl) { isTransitive = false }
}
