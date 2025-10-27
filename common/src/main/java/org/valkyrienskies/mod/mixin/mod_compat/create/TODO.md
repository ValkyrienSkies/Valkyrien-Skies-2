## List of things to fix in Create compat

(So mixins don't get forgotten and found commented out a year later)

MixinMinecartInstance - ~~commented out, og code moved to vanillin~~ This class is not needed since Implementing VisualEmbedding for the ships actually fixed the problem. (by Bunting_chj)

MixinFlwContraption - fix needs testing, ~~and transformLightboxToWorld commented out~~ LightBox was moved to ContraptionVisual. MixinContraptionVisual will handle it now. (by Bunting_chj)

Elevator Contraption - ~~Previous version had the elevator descend to abyss.~~ Not anymore so I think it's gone.

Hose Pulley - ~~Let's make them drain the world from the ship.~~ I think this was PR'd by someone else.

Package Entities - Spins on a chute or belt like a gyro.

Chain Conveyor BlockEntity(in fabric and forge) - Works fine but the dangling from acceleration of the ship is a bit jittery on sudden speed change.
