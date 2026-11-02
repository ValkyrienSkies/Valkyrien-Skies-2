package org.valkyrienskies.mod.common.render;

/**
 * Per-thread scratch state for {@code MixinSectionCompiler}. Lives outside
 * the {@code org.valkyrienskies.mod.mixin.*} package because mixin packages
 * are reserved for mixin classes and can't contain helper types directly
 * referenced from user code.
 */
public final class SparseSectionCompileState {
    public boolean active;
    public int nonAirRemaining;

    public void disable() {
        active = false;
    }

    public void enable(final int nonEmptyBlockCount) {
        active = true;
        nonAirRemaining = nonEmptyBlockCount;
    }
}
