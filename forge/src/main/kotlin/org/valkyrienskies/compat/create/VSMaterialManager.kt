package org.valkyrienskies.compat.create

import com.jozufozu.flywheel.backend.material.MaterialGroup
import com.jozufozu.flywheel.backend.material.MaterialManager
import com.jozufozu.flywheel.backend.state.IRenderState
import com.jozufozu.flywheel.core.WorldContext
import com.jozufozu.flywheel.core.shader.WorldProgram
import com.simibubi.create.content.contraptions.components.structureMovement.render.ContraptionProgram
import net.minecraft.core.BlockPos
import org.joml.Vector3i

class VSMaterialManager<P : WorldProgram>(
    context: WorldContext<P>,
    val flwShip: FlwShip,
    factory: (MaterialManager<P>, IRenderState) -> MaterialGroup<P>
) : MaterialManager<P>(context, factory, true) {

    init {
        val r = flwShip.ship.chunkClaim.getCenterBlockCoordinates(Vector3i())
        originCoordinate = BlockPos(r.x(), r.y(), r.z())
    }
}

class VSMaterialGroup(val flwShip: FlwShip, manager: MaterialManager<ContraptionProgram>, state: IRenderState) :
    MaterialGroup<ContraptionProgram>(manager, state) {

    override fun setup(program: ContraptionProgram) {
        flwShip.setup(program)
    }
}
