package org.valkyrienskies.mod.common.world;

import java.util.function.Supplier;
import org.valkyrienskies.mod.client.SeatVisualPose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Game/action geometry unless the caller explicitly opens a presentation scope. */
public final class RayQuery {
    private enum EntityPick { NONE, NEXT, ACTIVE }
    private record Context(Float partialTick, Entity viewer, Vec3 aim, Iterable<Entity> actors,
                           SeatVisualPose.Samples<Entity> seats, EntityPick pick) {
        Context withPick(EntityPick value) { return new Context(partialTick, viewer, aim, actors, seats, value); }
    }
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    private RayQuery() {}
    public static Float frameTime() {
        Context c = ACTIVE.get();
        return c == null ? null : c.partialTick;
    }
    public static Vec3 aim(Entity viewer) {
        Context c = ACTIVE.get();
        return c != null && c.viewer == viewer ? c.aim : null;
    }
    public static <T> T frame(float partialTick, Supplier<T> draw) {
        return frame(partialTick, java.util.List.of(), draw);
    }
    public static Iterable<Entity> presentedActors() {
        Context c = ACTIVE.get();
        return c == null ? java.util.List.of() : c.actors;
    }
    public static <T> T frame(float partialTick, Iterable<Entity> actors, Supplier<T> draw) {
        return scoped(new Context(partialTick, null, null, actors, new SeatVisualPose.Samples<>(), EntityPick.NONE), draw);
    }
    /** Mark the next six-argument ProjectileUtil invocation at the frame crosshair call site only. */
    public static <T> T crosshairCall(Supplier<T> run) {
        Context c = ACTIVE.get();
        return c == null || c.partialTick == null ? run.get() : scoped(c.withPick(EntityPick.NEXT), run);
    }
    /** Consume NEXT once. A recursive query (e.g. from a mod predicate) must not inherit the marker. */
    public static <T> T entityQuery(Supplier<T> run) {
        Context c = ACTIVE.get();
        if (c == null || c.pick == EntityPick.NONE) return run.get();
        return scoped(c.withPick(c.pick == EntityPick.NEXT ? EntityPick.ACTIVE : EntityPick.NONE), run);
    }
    public static boolean isFrameEntityPick() {
        Context c = ACTIVE.get();
        return c != null && c.pick == EntityPick.ACTIVE;
    }
    /** Null unless vanilla's frame pick is about to test a prepared direct-seat actor. */
    public static org.joml.Matrix4dc seatBasis(Entity actor) {
        var pose = visualSeat(actor);
        return pose == null ? null : pose.basis;
    }
    public static SeatVisualPose visualSeat(Entity actor) {
        Context c = ACTIVE.get();
        if (c == null || c.partialTick == null) return null;
        return c.seats.get(actor, () -> SeatVisualPose.sample(actor, c.partialTick));
    }
    public static <T> T action(Entity viewer, Vec3 aim, Supplier<T> pick) {
        return scoped(new Context(null, viewer, aim, java.util.List.of(), null, EntityPick.NONE), pick);
    }
    private static <T> T scoped(Context context, Supplier<T> run) {
        Context old = ACTIVE.get();
        ACTIVE.set(context);
        try { return run.get(); }
        finally { if (old == null) ACTIVE.remove(); else ACTIVE.set(old); }
    }
}
