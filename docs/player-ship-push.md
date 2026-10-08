# Push a ship

1. Empty your main hand.
2. Go within 2.5 blocks of a ship face.
3. Hold sneak and right-click the face.
4. Keep sneak held and move towards the face.

You do not need to keep the use button held. The force acts into the selected face.
Moving away does not pull the ship. Stop moving to stop the force.
Release sneak, hold an item, or move out of reach to end the action.
You cannot push a static ship or a ship on which you stand.
The force acts at the selected point, so a push away from the center can turn the ship.

The server settings are in `ShipInteractions`:

| Setting | Default | Function |
| --- | --- | --- |
| `playerShipPushing` | `true` | Enable player ship pushes. |
| `playerPushForce` | `100000.0` | Maximum force per player, in newtons. |
| `playerPushSpeed` | `1.5` | Contact speed at which the push force reaches zero, in meters per second. |

Ship mass, ground contact, and other forces still affect its motion.
The push action does not swing the player's hand.
If a world has the old default of `10000.0`, set `playerPushForce` to `100000.0` in its server config.
