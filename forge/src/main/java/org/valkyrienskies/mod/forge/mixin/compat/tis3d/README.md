Minimal TIS-3d Compatibility Mixins

- CustomClipContext
    - a custom clip context that does a Boolean AND on the Collision+Visual
      boxes
      (needed to fix infared packet raycast)

- MixinCasingBlockEntityRender
    - Fixes it skipping rendering on faces that are facing away from the player
      (when they are actually facing the player)
- MixinRenderContextImpl
    - Fixes distance culling checks on blocks that are in the shipyard
- MixinInfaredPacketEntity
    - Fixes Infared packets to/from ship to world/world to ship/ship to
      ship/ship within ship
