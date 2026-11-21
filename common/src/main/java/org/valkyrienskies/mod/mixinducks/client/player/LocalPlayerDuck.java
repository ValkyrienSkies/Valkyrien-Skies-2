package org.valkyrienskies.mod.mixinducks.client.player;

import org.joml.Vector3dc;
import org.valkyrienskies.mod.client.MountedCameraRotation;

public interface LocalPlayerDuck {
	Vector3dc vs$getVelocity();

    MountedCameraRotation vs$getMountedCameraRotation();

    void vs$updateMountedCameraRotation(boolean render);
}
