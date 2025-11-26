package org.valkyrienskies.mod.mixinducks.mod_compat.create;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * This class only exists because Creates Cluster class is private, and I can't get an access widener to work on it >.<
 */
public class CWCluster {
    public BlockPos anchor;
    public final Map<CWMergeEntry, Direction.AxisDirection> visibleFaces;
    public final Set<CWMergeEntry> visibleEdges;

    public CWCluster() {
        visibleEdges = new HashSet<>();
        visibleFaces = new HashMap<>();
    }

    public boolean isEmpty() {
        return anchor == null;
    }

    public void include(BlockPos pos) {
        if (anchor == null)
            anchor = pos;

        pos = pos.subtract(anchor);

        // 6 FACES
        for (Direction.Axis axis : Iterate.axes) {
            Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, axis);
            for (int offset : Iterate.zeroAndOne) {
                CWMergeEntry entry = new CWMergeEntry(axis, pos.relative(direction, offset));
                if (visibleFaces.remove(entry) == null)
                    visibleFaces.put(entry, offset == 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
            }
        }

        // 12 EDGES
        for (Direction.Axis axis : Iterate.axes) {
            for (Direction.Axis axis2 : Iterate.axes) {
                if (axis == axis2)
                    continue;
                for (Direction.Axis axis3 : Iterate.axes) {
                    if (axis == axis3)
                        continue;
                    if (axis2 == axis3)
                        continue;

                    Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, axis2);
                    Direction direction2 = Direction.get(Direction.AxisDirection.POSITIVE, axis3);

                    for (int offset : Iterate.zeroAndOne) {
                        BlockPos entryPos = pos.relative(direction, offset);
                        for (int offset2 : Iterate.zeroAndOne) {
                            entryPos = entryPos.relative(direction2, offset2);
                            CWMergeEntry entry = new CWMergeEntry(axis, entryPos);
                            if (!visibleEdges.remove(entry))
                                visibleEdges.add(entry);
                        }
                    }
                }

                break;
            }
        }
    }
}
