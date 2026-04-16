# Hexcasting Mixins

This section adds necessary Mixins for Hexcasting compatibility

## Mixins
### Hexcasting
- `MixinOpEntityRaycast` ensures raycasts targetting Shipyard entities are properly checked by transforming the hitbox AABB exists in Worldspace
- `MixinHexAdditionalRenderers` allows (Greater) Sentinels to render on Ships with correct position and scale
- `MixinOpTeleport` no longer sends Entities into the Shipyard but their relative Ship position instead
- `MixinOpTeleport$Spell` fixes the distance it uses to calculate how much of an inventory is dumped (feature of the spell)
### Hexal
- `MixinOpLink` and `MixinOpLinkOthers` fix distance checks for their respective spell
- `MixinRenderHelper` transform the source and sink positions for the line of particles
- `MixinServerLinkableHolder` fixes a distance check for breaking links
### Ephemera
- `MixinOpNetworkTeleport` fixes a few distance checks for the spell
