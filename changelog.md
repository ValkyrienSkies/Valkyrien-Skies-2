## VS 2.4

Massive backend, api, and QOL update. 

**Most notable changes:**
- Updated (Krunch) backend to be more performant
- Added PhysX backend option, currently a bit unstable but very performant. Stability will be increased in the future. 
- Revamped API for VS addons, with lots more documentation and requested additions
- Ships have aerodynamics now! No longer will they drift forever with zero-G. Shape based lift is also now a thing!
- Updated entity dragging. No longer should you be left behind by a fast ship, or be unable to interact with blocks at high speed. Multiplayer is also fixed, no more will your friends lag behind the ship! (mostly) 
- Many, many, many bug fixes. Like so many. We've actually lost track.  

**NOTE: ALL VS Addons will need to update to the new api. Allow time for them to update before updating your pack**

Other changes:
_non-exhaustive, and in no particular order_
- Added block shape based default masses
- Inter-ship frogport functionality
- Added a default assembly blacklist (for addons)
- New config system (accessible in create config viewer, or any other forge-config viewer)
- Many new language files
- Revamped /vs commands, including better autocomplete and new commands for changing backend settings
- Added atmospheric drag for ships, with datapackable values
- Added datapackable dimension gravity
- Some reworked mass values  
- Fixed players getting shipyarded when respawning on deleted ships
- Fixed toolbox interactions on ships
- Fixed compat with very many players
- Made cbc recoil configurable
- Fixed TerraFirmaCraft compat (again)
- Fixed voxy rendering with ships
- Fixed cbc shells not loading chunks correctly
- Fixed crash with lithum
- Fixed dispenser momentum
- Fixed miniship fog with sodium
- Added ship splitting (disabled by default in config)