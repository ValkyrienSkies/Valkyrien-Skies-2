## VS 2.5.0

Proper air pockets! As well as a many small fixes.

**Note: PhysX is still not the default. We have just chosen to use 2.5 for the air pocket release instead.**

#### Changes:
- Old (buggy) air pocket system has been entirely replaced by an incredibly impressive system.
  We owe a huge thanks to GigaBrawler for making this new system, with additional thanks to 
  Copper, Brickyboy, and Zyxkad for helping.
- Atmospheric max has been changed from 1000 to 2000. This should make propeller planes 
  much more controllable at altitudes above the clouds, where before they would stall pretty suddenly.
- Added `/vs dry` command to help dry your flooded ships

#### API changes:
- Fixed `transformFromWorldToNearbyShipsAndWorld` util function
- Added `getAllConnectedShips` to GTPA
- Added liquid overlap to GTPA
- Moved command related classes (aka `ShipArgument` and others). 
  **Breaking change**, but only imports will need changing
- The second version digit (2.**5**.0) is going to be bumped a bit more 
  frequently for medium-sized updates. You may want to anticipate this in your version ranges.

#### Bugfixes:
- Fixed buoyancy on large ships not being quite right
- Fixed an issue where Connectivity would still cause lag even if disabled
- Fixed a crash with Real Camera
- Fixed hexcasting and hexal compat issues
- Fixed copycats duping on assembly
- Fixed CBC autocannons breaking on VS ships
- Fixed a crash with Create: Hypertubes (sorry it took so long Rok!)
- Fixed visual artifacts with the ship AABB when at large coordinates
