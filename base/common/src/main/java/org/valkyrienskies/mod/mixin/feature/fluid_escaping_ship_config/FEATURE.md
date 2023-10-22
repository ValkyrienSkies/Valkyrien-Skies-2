### Fluid Escaping

This feature makes fluids not flow outside the existing boundingbox of the ship.
It can be enabled or disabled via config.

#### Mixins

* `MixinFlowingFluid#beforeCanSpreadTo`
    * If config is enabled check if the water wants to flow outside the ship's
      boundingbox.
    * If it does, cancel the event.
