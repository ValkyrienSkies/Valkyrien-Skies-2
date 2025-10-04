## List of things to fix in Create compat

(So mixins don't get forgotten and found commented out a year later)

MixinMinecartInstance - ~~commented out, og code moved to vanillin~~ This class is not needed since Implementing VisualEmbedding for the ships actually fixed the problem. However, There is a bug that is caused by entities on a ship loading before the clientShip is loaded. (by Bunting_chj)

MixinFlwContraption - fix needs testing, ~~and transformLightboxToWorld commented out~~ LightBox was moved to ContraptionVisual. MixinContraptionVisual will handle it now. (by Bunting_chj) 

MixinStorage - creates mixin to the impl class Storage. Making a decorator for visualizer and decorating every visualizer registered might be more change-safe. (by Bunting_chj)

MixinAbstractEntityVisual - find out if it is safe to just set noCulling for entities on a ship. Currently intercepts culling check at AbstractEntityVisual only.
