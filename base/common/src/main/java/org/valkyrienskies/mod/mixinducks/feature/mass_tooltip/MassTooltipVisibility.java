package org.valkyrienskies.mod.mixinducks.feature.mass_tooltip;

import net.minecraft.world.item.TooltipFlag;

public enum MassTooltipVisibility {
    ALWAYS,
    ADVANCED,
    DISABLED;

    public boolean isVisible(final TooltipFlag flag) {
        return this.equals(ALWAYS) ||
            (this.equals(ADVANCED) && flag.isAdvanced());
    }
}
