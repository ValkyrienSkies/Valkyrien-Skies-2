package org.valkyrienskies.mod.compat.iris;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.world.level.block.state.BlockState;

public class IrisCompat {

    /** Returned when the material id cannot be resolved — no pack loaded, or the pack does not map it. */
    public static final int UNKNOWN_MATERIAL_ID = -1;

    public static boolean isIrisShaderActive() {
        IrisApi irisApi = IrisApi.getInstance();
        return irisApi != null && irisApi.isShaderPackInUse();
    }

    /**
     * The material id the active shaderpack assigned to a block state, as its shaders see it in
     * {@code mc_Entity.x}.
     *
     * <p>These ids come from each pack's own {@code block.properties}, so they are a property of the
     * loaded pack rather than of Minecraft or of Iris. Water is {@code 32000} under Complementary and
     * {@code 10001} under Photon, and under a third pack it is whatever that pack says — so anything
     * hand-building geometry a pack should treat as a given block has to ask for the id rather than
     * assume one.</p>
     *
     * @return the pack's id, or {@link #UNKNOWN_MATERIAL_ID} if there is no pack loaded or it does not
     *     map this state
     */
    public static int getBlockMaterialId(final BlockState state) {
        final Object2IntMap<BlockState> ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();
        if (ids == null || !ids.containsKey(state)) {
            return UNKNOWN_MATERIAL_ID;
        }
        return ids.getInt(state);
    }
}
