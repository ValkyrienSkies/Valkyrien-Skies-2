/**
 * Per-mob spawn hooks that make vanilla's spawn predicates ship-aware.
 *
 * <p>These all wrap vanilla static spawn-rule helpers, which mods love to {@code @Overwrite}. Mixin
 * refuses to inject into a method merged by another mixin unless our priority is strictly higher, so
 * at the default 1000 an unrelated overwrite is a hard {@code InvalidInjectionException} at load
 * time; and once that overwrite wins, the instruction we wrap is usually gone anyway.
 *
 * <p>Hence {@code priority = 1500} on every mixin here and {@code require = 0} on every injector: we
 * apply after a default-priority overwrite and wrap it if the vanilla call survived, and step aside
 * silently if it did not. The default {@code expect = 1} still warns, so a real mapping regression
 * stays visible. Stepping aside is correct rather than a compromise — a mod that overwrote the spawn
 * rule deliberately replaced the very check we were making ship-aware.
 */
package org.valkyrienskies.mod.mixin.feature.mob_spawning.per_mob;
