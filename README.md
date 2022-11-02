

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

## Installation

You can download official releases of Valkyrien Skies from our [website](https://www.valkyrienskies.org/download)

## Development

Valkyrien Skies 2 source code is split between the code in this repository, and
the code in [vs-core](https://github.com/ValkyrienSkies/vs-core).

The Minecraft version dependent code lives in this repository, and the version
independent code lives in [vs-core](https://github.com/ValkyrienSkies/vs-core).

### IntelliJ

1. Clone the
   repo: `git clone --recurse-submodules https://github.com/ValkyrienSkies/Valkyrien-Skies-2`
2. Open the project in IntelliJ, using Java 17
3. Import the gradle project, sync gradle

## Troubleshooting

### Running Forge in dev environment
Occasionally forge will break will strange ways. When this occurs, delete all the `build` folders, delete the `.gradle` folder of the `forge` project, and then refresh gradle.
