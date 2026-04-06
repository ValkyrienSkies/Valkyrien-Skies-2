## VS 2.4.11

Bug fixes galore

#### Changes:
- Old (buggy) air pocket system is now able to be turned off, and is off by default. A better airpocket system is still in progress.  
- Atmospheric max has been changed from 1000 to 2000. This should make propeller planes 
  much more controllable at altitudes above the clouds, where before they would stall pretty suddenly.
- Added `/vs dry` command for once ship flooding is added. 

#### API changes:
- Fixed `transformFromWorldToNearbyShipsAndWorld` util function
- Added `getAllConnectedShips` to GTPA
- Added liquid overlap to GTPA
- Moved command related classes (aka `ShipArgument` and others). 
  **Breaking change**, but only imports will need changing

#### Bugfixes:
- Fixed buoyancy on large ships not being quite right
- Fixed an issue where Connectivity would still cause lag even if disabled
- Fixed a crash with Real Camera
- Fixed hexcasting and hexal compat issues
- Fixed copycats duping on assembly
- Fixed CBC autocannons breaking on VS ships
- Fixed a crash with Create: Hypertubes (sorry it took so long Rok!)
- Fixed visual artifacts with the ship AABB when at large coordinates
