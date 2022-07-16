
# <img src="vs_logo.png" width="48" height="48">alkyrien Skies 2
[![Discord](https://img.shields.io/discord/244934352092397568.svg)](https://discord.gg/rG3QNDV)

The Airships Mod to end all other Airships Mods. Better compatibility, performance, collisions, interactions and physics than anything prior!

## Installation

### Downloading
There are no official and stable releases yet, but you can currently download builds from [GitHub Packages](https://github.com/orgs/ValkyrienSkies/packages?repo_name=Valkyrien-Skies-2).
Make sure to download the file that ends with `.jar`, and that isn't the `sources.jar`.

## Development

Valkyrien Skies 2 source code is split between the code in this repository, and the code in [vs-core](https://github.com/ValkyrienSkies/vs-core).

The Minecraft version dependent code lives in this repository, and the version independent code lives in [vs-core](https://github.com/ValkyrienSkies/vs-core).

### IntelliJ
1. Clone the repo: `git clone --recurse-submodules https://github.com/ValkyrienSkies/Valkyrien-Skies-2`
2. Open the project in IntelliJ, using Java 17
3. Import the gradle project, sync gradle

## Troubleshooting

### Running Forge in dev environment
Occasionally forge will break will strange ways. When this occurs, delete all the `build` folders, delete the `.gradle` folder of the `forge` project, and then refresh gradle.
