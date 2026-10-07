# Ship debug renderer

Use `/vs debug renderer <mode>` to set the mode for your player:

- `none`: Stop the renderer.
- `markers`: Show contact points, bounding boxes, joints, force arrows, axes, and origins.
- `text`: Show the text above each ship.
- `full`: Show markers and ship text.

Use `/vs debug renderer` to start full mode or stop an active renderer.
The command requires permission level 2. F3+B is not required.

The renderer shows ships within 128 blocks. It gets a new sample after each
physics tick. The default physics rate is 60 ticks each second. A separate
worker sends the data. The worker and the client keep only the newest pending
sample if they cannot process all samples. Arrows, contacts, and ship states
use the newest sample. Text values update up to 20 times each second.
The server sends contact points, joints, and force application points in modes
that show markers. Ship text uses the force totals in the physics sample.
Each player can see up to 64 ships. Each physics sample can contain 256 contact points, 256 joints,
and 16 force application points per ship. The display shows when a limit is reached.

The ship box is green when the ship is awake, blue when it is asleep, and grey
when it is static. A fragment has an orange centre marker and its parent ID.
An awake fragment also has an orange box.

Yellow arrows show the total applied force and its application points.
Red arrows show gravity. Cyan arrows show aerodynamic drag. Violet arrows
show aerodynamic lift and wing forces. White arrows show velocity. Pink arrows
show applied torque. Arrow length uses a log scale so small and large values
remain visible. The labels give the values in standard units.

Lime arrows show an estimate of the net force from mass times the velocity
change divided by the physics time step. This estimate includes the effect
of contacts and fluid forces. It does not separate those forces.

Applied force and torque include forces sent through the VS body force methods.
Gravity is separate because the native engine applies it. Native contact forces
and native fluid forces are not available as separate vectors through this API.
Contact points have red markers and normal lines. Joints have gold anchor
markers, a line between the anchors, and a label with their ID and type.

The labels show mass, weight from dimension gravity, speed, angular speed,
age, and distance to the nearest physics origin. RGB lines show the ship axes.
The small box at the centre marks the centre of mass.

This native engine checkout uses one world origin at `(0, 0, 0)` per dimension.
The origin display and distance use that position. The packet accepts a list
of origins if the native engine later exposes more origins.

New ships made by the mod store their creation time in a saved ship
attachment. Assembled fragments also store their parent ID in that
attachment. Age uses game time in the overworld and continues through dimension
changes and world reloads. Ships from older saves do not have a creation time
or a parent ID. Their label says `Tracked age`, starting with the first debug
sample. Old fragment ancestry cannot be recovered from the current ship data.

The client clears the renderer when it disconnects. The server removes debug
users when they leave or lose permission. Physics data collection stops when
no debug user has nearby ships.
