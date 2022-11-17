### Shipyard entities

This feature adds that entity can be handled by certain VSEntityHandlers.
Is used for minecarts, paintings, itemframes and more. Things that should live
in the ship.

#### Mixins

* `MixinEntity#setPosHandling1` Handles changes to the entity's position.
* `MixinEntity#setPosHandling2` Handles changes to the entity's position.
    * This is relevant for detecting if a entity is going out the ship's bounds.
* `MixinEntity#positionRider` Handles mounted entities on other entities.
    * This is relevant for allowing world entities mounting shipyard entities.
* `MixinEntityRenderDispatcher#render` Handles rendering of entities.
    * This is relevant for rendering entities on ships.
* `MixinEntityRenderDispatcher#shouldRender` Handles rendering of entities.
    * This is relevant for rendering entities on ships.
