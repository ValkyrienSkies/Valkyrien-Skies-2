package org.valkyrienskies.mod.mixin.mod_compat.jei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.core.search.PrefixInfo;
import mezz.jei.core.search.SearchMode;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Unmodifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.ClientBlockInfo;
import org.valkyrienskies.mod.client.ClientBlockStateInfo;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.jei.NumericAttributeStorage;

@Mixin(ElementPrefixParser.class)
public abstract class MixinElementPrefixParser {
    @Shadow
    protected abstract void addPrefix(PrefixInfo<IListElementInfo<?>, IListElement<?>> info);

    @Inject(
        method = "<init>",
        at = @At("RETURN"),
        remap = false
    )
    private void injectInit(IIngredientManager ingredientManager, IIngredientFilterConfig config,
        IColorHelper colorHelper, CallbackInfo ci) {

        if (!ClientBlockStateInfo.INSTANCE.getClientHasMassInfo()) return;
        if (!VSGameConfig.CLIENT.getJeiSearch()) return;
        addPrefix(new PrefixInfo<>(VSGameConfig.CLIENT.getSearchPrefix().charAt(0), () -> SearchMode.REQUIRE_PREFIX, this::valkyrienskies$getCreativeTabsStrings, NumericAttributeStorage::new));
    }

    @Unique
    private @Unmodifiable Collection<String> valkyrienskies$getCreativeTabsStrings(IListElementInfo<?> info) {
        ItemStack itemStack = info.getElement().getTypedIngredient().getItemStack().orElse(ItemStack.EMPTY);
        if (itemStack.isEmpty()) {
            return List.of();
        } else {
            if (!(itemStack.getItem() instanceof BlockItem blockItem)) {
                return List.of();
            }

            Block block = blockItem.getBlock();
            ArrayList<String> weights = new ArrayList<>();

            for (BlockState state : block.getStateDefinition().getPossibleStates()) {

                // grab values from ClientBlockStateInfo instead of using the MassDatapackResolver as on the client-side values are stored there
                ClientBlockInfo blockInfo = ClientBlockStateInfo.INSTANCE.getBlockInfo(state);
                double mass = blockInfo != null ? blockInfo.getMass() : 0.0;
                double friction = blockInfo != null ? blockInfo.getFriction() : 0.0;
                double elasticity = blockInfo != null ? blockInfo.getElasticity() : 0.0;

                // JEI requires our data passed to searcher be a string so we format the string and
                // un-format it later
                weights.add("f:%s e:%s m:%s".formatted(friction, elasticity, mass));
            }

            return weights;
        }
    }
}
