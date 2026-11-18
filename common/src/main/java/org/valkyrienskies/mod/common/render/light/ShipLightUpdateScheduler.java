package org.valkyrienskies.mod.common.render.light;

public final class ShipLightUpdateScheduler {
    private Object level;
    private boolean pending;

    public void request() {
        pending = true;
    }

    public boolean beginFrame(final Object currentLevel) {
        if (currentLevel == null) {
            clear();
            return false;
        }
        final boolean update = pending || level != currentLevel;
        level = currentLevel;
        pending = false;
        return update;
    }

    public void clear() {
        level = null;
        pending = false;
    }
}
