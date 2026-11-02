package org.valkyrienskies.mod.mixinducks.mod_compat.create;

import com.simibubi.create.content.contraptions.StructureTransform;
import javax.annotation.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;

public interface MixinAbstractContraptionEntityDuck {
    void vs$setForceStall(boolean forceStall);

    StructureTransform getStructureTransform();

    void vs$setSegmentId(int segmentId);
    int vs$getSegmentId();

    void vs$setSegmentOwner(long bodyId, boolean isWorld);
    long vs$getSegmentBodyId();
    boolean vs$isWorldSegment();

    void vs$setLastSegmentPose(Vector3dc position, Quaterniondc rotation);
    @Nullable Vector3dc vs$getLastSegmentPosition();
    @Nullable Quaterniondc vs$getLastSegmentRotation();
}
