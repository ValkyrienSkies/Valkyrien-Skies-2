package org.valkyrienskies.mod.common.render.batched;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

final class ShipTranslucentOrder<T> {
    @FunctionalInterface
    interface Sort<T> {
        void run(ArrayList<T> sections, double x, double y, double z);
    }

    private final ArrayList<T> sections = new ArrayList<>();
    private final Sort<T> sort;
    private Object mesh;
    private double cameraX, cameraY, cameraZ;

    ShipTranslucentOrder(final Sort<T> sort) {
        this.sort = sort;
    }

    ArrayList<T> sections() {
        return sections;
    }

    boolean update(final Object currentMesh, final Collection<T> source,
        final double x, final double y, final double z) {
        Objects.requireNonNull(currentMesh, "currentMesh");
        if (mesh == currentMesh && same(cameraX, x) && same(cameraY, y) && same(cameraZ, z)) {
            return false;
        }
        sections.clear();
        sections.addAll(source);
        sort.run(sections, x, y, z);
        mesh = currentMesh;
        cameraX = x;
        cameraY = y;
        cameraZ = z;
        return true;
    }

    void clear() {
        mesh = null;
        sections.clear();
    }

    private static boolean same(final double a, final double b) {
        return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
    }
}
