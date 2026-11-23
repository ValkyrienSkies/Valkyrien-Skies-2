package org.valkyrienskies.mod.client;

import java.util.IdentityHashMap;
import java.util.function.Supplier;

/** Render-thread scopes. An A -> B -> A view borrows A's prepared state rather than preparing its entities twice. */
public final class LevelViewScopes<L, F> {
    private final IdentityHashMap<L, F> prepared = new IdentityHashMap<>();
    private F current;

    public F current() { return current; }

    public interface Draw<F> { void run(F state, F previous, boolean first); }

    public void withView(L level, Supplier<F> create, Supplier<Runnable> suspendHits, Draw<F> draw) {
        F previous = current;
        F frame = prepared.get(level);
        boolean first = frame == null;
        if (first) { frame = create.get(); prepared.put(level, frame); }
        Runnable restoreHits = previous != null ? suspendHits.get() : null;
        current = frame;
        try { draw.run(frame, previous, first); }
        finally {
            current = previous;
            if (first) prepared.remove(level);
            if (restoreHits != null) restoreHits.run();
        }
    }
}
