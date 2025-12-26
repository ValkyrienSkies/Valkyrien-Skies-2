package org.valkyrienskies.mod.mixinducks.mod_compat.create;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import net.createmod.catnip.lang.Lang;

public interface IDeployerBehavior {
    default ScrollOptionBehaviour<WorkingMode> valkyrienskies$get_working_mode() {
        return null;
    }

    enum WorkingMode implements INamedIconOptions {

        ORIGINAL(AllIcons.I_MOVE_PLACE),
        IN_WORLD(AllIcons.I_MOVE_PLACE_RETURNED),
        ;

        private String translationKey;
        private AllIcons icon;

        WorkingMode(AllIcons icon) {
            this.icon = icon;
            translationKey = "misc.valkyrienskies.create.deployer_working_mode." + Lang.asId(name());
        }

        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }

    }
}
