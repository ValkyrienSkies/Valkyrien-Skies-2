{
  description = "Valkyrien Skies 2 dev env";

  inputs = {
    nixpkgs = {
      url = "github:nixos/nixpkgs/nixos-unstable";
    };
    flake-utils = {
      url = "github:numtide/flake-utils";
    };
  };
  outputs =
    { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs { inherit system; };

        gradle_path = "./gradlew";

        runFabric = pkgs.writeShellScriptBin "runFabric" (''${gradle_path} :fabric:runClient'');
        runFabricServer = pkgs.writeShellScriptBin "runFabricServer" (''${gradle_path} :fabric:runServer'');

        runForge = pkgs.writeShellScriptBin "runForge" (''${gradle_path} :forge:runClient'');
        runForgeServer = pkgs.writeShellScriptBin "runForgeServer" (''${gradle_path} :forge:runServer'');

      in
      {
        devShell = pkgs.mkShell {
          shellHook = ''
            export LD_LIBRARY_PATH="''${LD_LIBRARY_PATH}''${LD_LIBRARY_PATH:+:}${pkgs.libglvnd}/lib"
          '';

          buildInputs = with pkgs; [
            jdk17
            gradle_7
            zenity

            runFabric
            runFabricServer

            runForge
            runForgeServer

          ];
        };
      }
    );
}
