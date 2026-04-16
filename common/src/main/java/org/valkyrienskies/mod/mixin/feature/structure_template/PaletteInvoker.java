package org.valkyrienskies.mod.mixin.feature.structure_template;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(StructureTemplate.Palette.class)
public interface PaletteInvoker {
    @Invoker("<init>")
    static StructureTemplate.Palette invokeInit(List<StructureTemplate.StructureBlockInfo> blocks) {
        throw new AssertionError();
    }
}
