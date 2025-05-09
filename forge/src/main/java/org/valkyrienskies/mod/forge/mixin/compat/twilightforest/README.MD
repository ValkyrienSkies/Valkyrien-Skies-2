Just a smidgen of compatibility code for twilight forest

- ChunkGeneratorTwilightMixin
  - Fixes world generation not being cancelled within the shipyard in the
    twilight forest. TF uses its own ChunkGenerator implementation but it has
    all the same methods as the vanilla NoiseBasedChunkGenerator so the same
    mixins work.
