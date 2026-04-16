# Hexcasting Compatibility

## HexcastingCompat.kt
- Handles registering all `ShipAmbit` instances using `register` due to how hexcasting handles such things.

## ShipAmbit.kt
- Handles both permissions checks and ambit checks based on Ship context (both of the Ship the caster may be on and the Ship the target position may be on).
- This is meant to be extended by `FabricShipAmbit` and `ForgeShipAmbit` to handle modloader-specific Hexcasting addon compatibilities such as HexTweaks' `ComputerCastEnv` or Hexal's `WispCastEnv`.

## HexTweaksCompat.kt
- A shared class used to get a caster position from `CompterCastEnv`.
- Should only be referred to in `FabricShipAmbit` and `ForgeShipAmbit` after checking if HexTweaks is loaded.
