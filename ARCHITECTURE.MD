# Valkyrien Skies Architecture

## Dependency Graph (non-exhaustive)

```mermaid
  graph
     eureka-1.16[Eureka 1.16] --> vs2-1.16
     eureka-1.17[Eureka 1.17] --> vs2-1.17
     
     subgraph "You Are Here"
     vs2-1.16[VS2 1.16]
     vs2-1.17[VS2 1.17]
     end
     
     vs2-1.16[VS2 1.16] --> core[VS Core]
     vs2-1.17[VS2 1.17] --> core
     core --> physics-api[VS Physics API]
     core --> krunch[Krunch]
     krunch --> physics-api
```

## Chunk Claims

Each ship is allocated a portion of the world called a ChunkClaim. A Minecraft
chunk is composed of 16 blocks, a ChunkClaim is composed of 512 chunks.
ChunkClaims are unique across dimensions, which means a ship in the Overworld
could not have the same ChunkClaim as a ship in the Nether.

## Ship Class Types

What class should I use if I want to represent a ship?

```mermaid
flowchart LR
    side{Which Side?} --> |Client*| ShipObjectClient
    side --> |Server| server-loaded{Loaded?}
    side --> |Either| either-loaded{Loaded?}
    server-loaded --> |Yes| ShipObjectServer
    server-loaded --> |Maybe| ShipData
    either-loaded --> |Yes| ShipObject
    either-loaded --> |Maybe| ShipDataCommon
```

* No such thing as an unloaded ship on the client
