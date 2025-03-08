

<p align="center">
<img src="vs_logo.png" width="200" height="200">
</p>
<h1 align="center">
Valkyrien Skies 2
</h1>
<p align="center">
<a href="https://www.valkyrienskies.org/">Website</a> - <a href="https://www.curseforge.com/minecraft/mc-mods/valkyrien-skies">CurseForge</a> - 
<a href="https://modrinth.com/mod/valkyrien-skies">Modrinth</a> - <a href="https://wiki.valkyrienskies.org/wiki/Main_Page">Wiki</a> - <a href="https://discord.gg/rG3QNDV">Discord</a>
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
2. Open the project in IntelliJ, using Java 21
3. Import the gradle project, sync gradle

## Troubleshooting

### Running Forge in dev environment
Occasionally forge will break in strange ways. When this occurs, delete all the `build` folders, delete the `.gradle` folder of the `forge` project, and then refresh gradle.

### Change JVM arguments for small computers
`Error occurred during initialization of VM
Could not reserve enough space for 4194304KB object heap`
For patch the problem go to gradle.properties and change `org.gradle.jvmargs=-Xmx4096M` to `org.gradle.jvmargs=-Xmx1024` or `org.gradle.jvmargs=-Xmx4G -XX:MaxMetaspaceSize=1G` to `org.gradle.jvmargs=-Xmx1G -XX:MaxMetaspaceSize=1G`

## Attributions

Valkyrien Skies 2 was originally created by Triode and Rubydesic. You can check
other contributors by viewing the git history.

The Create compatibility code was originally and largely written by [FluffyJenkins](https://github.com/FluffyJenkins/), 
but the git history was clobbered when we transferred the code from Clockwork
