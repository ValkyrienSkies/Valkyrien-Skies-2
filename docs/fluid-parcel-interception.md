# Airborne fluid interception

FF05 lets ships catch fluid parcels that have already been emitted into the world. Receiver motion includes translation, rotation and uniform scale. It uses the shared fluid grid and partial boundaries from FF03/FF04, including fluid domains outside the main hull.

The earliest contact determines the receiver. World terrain wins ties; simultaneous ship contacts use ship ID order. A solid wall blocks the parcel. Open fluid cells accept at most their available physical capacity, with scale accounted for in world volume. Full, incompatible, unloaded or busy receivers do not gain water; the remaining parcel keeps its volume and is stopped by any intervening hull.

Acceptance runs on the physics thread. Core retains its result until the game applies the matching parcel debit. Both the pending request and any applied-but-unfinished parcel motion survive save/reload. Direct outlet transfers have already been deducted before their remaining world parcels reach this path.

Interception uses the existing sealed-topology, ship-flooding, cellular-automaton and inter-body-transfer settings. It adds no separate toggle. All dependencies must include FF05; older core implementations leave parcels on their existing world-only path.

Queries have bounded receiver and motion budgets. Extreme motion that exceeds the sweep budget stops that parcel step without transferring water. During a physics capacity wait, receivers use their current game pose; the last game tick's ship motion is not replayed for each parcel step. Delayed parcel catch-up steps likewise use the current pose until the current motion interval.

This does not change bucket placement, vanilla source behavior, puddle flow or fluid mixing. Live acceptance should check a moving tank catching an independent airborne stream on both Fabric and Forge, including save/reload with a catch in flight.
