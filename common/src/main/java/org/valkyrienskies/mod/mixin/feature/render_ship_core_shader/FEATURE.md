### Normal-Aware Ship Rendering (Custom Core Shader)

This feature makes ships use world space normals for shading instead of pre-baked
brightness value baked into vertex colors along with lights and ambient occlusion.
This is achieved by disabling vanilla fake shading and swapping shaders for block
render types with their modified versions that replicate vanilla shading, similar
to how Indigo calculates shading for rotated quads based on their normals.
It can be enabled or disabled via config.

#### Mixins

* `MixinRenderChunkRegion#maxShadeForShips`
    * If config is enabled, return the max brightness value. This is used both
  by vanilla `ModelBlockRenderer` and Fabric `AbstractBlockRenderContext`.
    * If the block is rendered without shading (tall grass, torches, etc),
  return an even larger value that is later processed by another mixin.

* `MixinVertexConsumer#putBulkData`
    * Check if the brightness value is offset to signal a no-shade quad, offset
  it back and force alpha value of new vertices to zero. This signals no-shade
  to our custom shader.
