### Shipyard entities

This feature adds that entity can be handled by certain VSEntityHandlers.
Is used for minecarts, paintings, itemframes and more. Things that should live
in the ship.

#### Mixins

* `MixinEntity#handlePosSet` Handles changes to the entity's position.
    * This is relevant for detecting if a entity is going out the ship's bounds.
* `MixinEntity#positionRider` Handles mounted entities on other entities.
    * This is relevant for allowing world entities mounting shipyard entities.
* `MixinEntity#preventSavingVehiclePosAsOurPos` Prevents the game from saving
  the vehicle position as the entities' position.
    * This fixes players falling through the world when loading if they were
    mounted to a ship entity when the game saved.
* `MixinEntityRenderDispatcher#render` Handles rendering of entities.
    * This is relevant for rendering entities on ships.
* `MixinEntityRenderDispatcher#shouldRender` Handles rendering of entities.
    * This is relevant for rendering entities on ships.
* `MixinServerLevel/MixinClientLevel#configureEntitySections`
    * Sets our own level field in EntitySectionManager
* `MixinEntitySectionStorage#shipSections`
    * Makes getEntities return the entities in the ship section

#### Extra notes

getEntities is important for entity interaction collision and more.
