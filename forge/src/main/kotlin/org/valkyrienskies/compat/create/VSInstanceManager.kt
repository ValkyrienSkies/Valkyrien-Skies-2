package org.valkyrienskies.compat.create

import com.jozufozu.flywheel.backend.Backend
import com.jozufozu.flywheel.backend.instancing.IDynamicInstance
import com.jozufozu.flywheel.backend.instancing.IInstance
import com.jozufozu.flywheel.backend.instancing.ITickableInstance
import com.jozufozu.flywheel.backend.instancing.tile.TileInstanceManager
import net.minecraft.world.level.block.entity.BlockEntity

class VSInstanceManager(ship: FlwShip) : TileInstanceManager(ship.materialManager) {

    fun get(be: BlockEntity): IInstance? = this.getInstance(be, false)

    // Both copy-pasted from TileInstanceManager to skip the mixin
    override fun remove(obj: BlockEntity) {
        if (!Backend.getInstance().canUseInstancing()) return

        if (canInstance(obj)) {
            val instance = getInstance(obj, false)
            instance?.let { removeInternal(obj, it) }
        }
    }

    override fun createInternal(obj: BlockEntity): IInstance {
        val renderer = createRaw(obj)

        if (renderer != null) {
            renderer.updateLight()
            instances[obj] = renderer
            if (renderer is IDynamicInstance) dynamicInstances[obj] = renderer as IDynamicInstance?
            if (renderer is ITickableInstance) tickableInstances[obj] = renderer as ITickableInstance?
        }

        return renderer!!
    }
}
