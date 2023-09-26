package net.optifine;

/**
 * Dummy class that exists so we can generate certain Mixins. This class is removed from the jar after compilation by
 * gradle.
 */
public class Config {
    public static boolean isVbo() {
        throw new IllegalStateException();
    }

    public static boolean isRenderRegions() {
        throw new IllegalStateException();
    }

    public static boolean isFogOn() {
        throw new IllegalStateException();
    }

    public static boolean isShaders() {
        throw new IllegalStateException();
    }
}
