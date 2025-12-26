<p align="center">
<img src="vs_logo.png" width="200" height="200">
</p>
<h1 align="center">
Valkyrien Skies
</h1>
<p align="center">
<a href="https://www.valkyrienskies.org/"><img src="https://img.shields.io/badge/Website-white?style=for-the-badge&logo=html5&logoColor=black" alt="Website"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/valkyrien-skies"><img src="https://img.shields.io/badge/CurseForge-white?style=for-the-badge&logo=curseforge" alt="CurseForge"></a>
<a href="https://modrinth.com/mod/valkyrien-skies"><img src="https://img.shields.io/badge/Modrinth-white?style=for-the-badge&logo=modrinth" alt="Modrinth"></a>
<a href="https://wiki.valkyrienskies.org/wiki/Main_Page"><img src="https://img.shields.io/badge/Wiki-white?style=for-the-badge&logo=wikipedia&logoColor=black" alt="Wiki"></a>
<a href="https://discord.gg/rG3QNDV"><img src="https://img.shields.io/badge/Discord-white?style=for-the-badge&logo=discord" alt="Discord"></a>
</p>

*The physics mod to end all other physics mods. Better compatibility,
performance, collisions, interactions and physics than anything prior!*

![2022-11-01_21 58 07](https://user-images.githubusercontent.com/26909616/199406363-38e1d032-9c18-4aef-a74a-23f4b268e6ad.png)


## Installation

You can download official releases of Valkyrien Skies from our [website](https://www.valkyrienskies.org/download)

## Development

### IntelliJ

1. Clone the
   repo: `git clone --recurse-submodules https://github.com/ValkyrienSkies/Valkyrien-Skies-2`
2. Open the project in IntelliJ, using Java 17
3. Import the gradle project, sync gradle

### Including in your mod

*(Remember that VS requires KotlinForForge in Forge development environments! The specific version can be found in `gradle.properties`.)*

To include Valkyrien Skies in your mod, you can use the following gradle repository-

```groovy
repositories {
   //...
   maven {
      name = "Valkyrien Skies"
      url = "https://maven.valkyrienskies.org"
   }
   //...
}
```

and dependency-
```groovy
dependencies {
   //...
   modApi("org.valkyrienskies:valkyrienskies-120-//loader//:${vs2_version}") {
      exclude group: 'com.simibubi', module: ''
      exclude group: 'dev.engine-room', module: ''
      exclude group: 'com.jozufozu', module: ''
   }
   implementation("org.valkyrienskies.core:api:${vscore_version}") {
      exclude group: 'org.joml', module: ''
   }
   implementation("org.valkyrienskies.core:internal:${vscore_version}") {
      exclude group: 'org.joml', module: ''
   }
   implementation("org.valkyrienskies.core:util:${vscore_version}") {
      exclude group: 'org.joml', module: ''
   }
   //...
}
```

Replace `-//loader//` with `-(your modloader)`, or use `-common` if you're in a multiloader project on the Common platform.

You can get versions of VSCore from this project's `gradle.properties` file, and specific versions of VS can be pulled using the version + beta + first 10 characters of the commit's Git hash.

#### Currently, VS2 is on `2.5.0-beta.3`

### A note about 'Debug' items
The debug items present in Valkyrien Skies are not indicative of the ideal way of executing their concepts; they are nothing more than simple test items designed to make debugging development easier, as implied by their name.
Additionally, any class with Debug in the name should not be used outside VS itself.

## Troubleshooting

### Running Forge in dev environment
Occasionally forge will break in strange ways. When this occurs, delete all the `build` folders, delete the `.gradle` folder of the `forge` project, and then refresh gradle.

#### cpw.mods.securejarhandler Issue
Additionally, as of a recent version of Forge, they changed their dependencies and now the generated buildscript by IntelliJ can fail. If it complains about cpw.mods, follow these steps:

1. Go to your user .gradle directory, located in C:/Users/**/.gradle
2. Navigate to caches\modules-2\files-2.1\cpw.mods\securejarhandler
3. If it's using 1.0.8, delete it and refresh gradle.
4. Now, edit the Minecraft Client (:forge) run configuration:
5. Replace the mention of 1.0.8/(hash)/securejarhandler-1.0.8.jar with:
   `caches\modules-2\files-2.1\cpw.mods\securejarhandler\2.1.10\51e6a22c6c716beb11e244bf5b8be480f51dd6b5\securejarhandler-2.1.10.jar`

Forge should now run in dev.

### Change JVM arguments for small computers
`Error occurred during initialization of VM
Could not reserve enough space for 4194304KB object heap`
For patch the problem go to gradle.properties and change `org.gradle.jvmargs=-Xmx4096M` to `org.gradle.jvmargs=-Xmx1024` or `org.gradle.jvmargs=-Xmx4G -XX:MaxMetaspaceSize=1G` to `org.gradle.jvmargs=-Xmx1G -XX:MaxMetaspaceSize=1G`

## Attributions

Valkyrien Skies 2 was originally created by Triode and Rubydesic. You can check
other contributors by viewing the git history.

The Create compatibility code was originally and largely written by [FluffyJenkins](https://github.com/FluffyJenkins/), 
but the git history was clobbered when we transferred the code from Clockwork
