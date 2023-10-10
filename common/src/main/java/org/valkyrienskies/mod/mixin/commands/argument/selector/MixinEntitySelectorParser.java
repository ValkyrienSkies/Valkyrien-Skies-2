package org.valkyrienskies.mod.mixin.commands.argument.selector;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * This fixes EntitySelectorParser making suggestions in @v ShipArguments
 */
@Mixin(EntitySelectorParser.class)
public class MixinEntitySelectorParser {
    @Shadow
    @Final
    private StringReader reader;

    @Shadow
    private void finalizePredicates() {
        throw new IllegalStateException();
    }

    @Shadow
    public EntitySelector getSelector() {
        return null;
    }

    @Inject(method = "parse", at = @At("HEAD"), cancellable = true)
    private void preParse(final CallbackInfoReturnable<EntitySelector> cir) {
        // If this starts with '@v' then don't suggest anything
        if (this.reader.canRead(2) && this.reader.peek() == '@' && this.reader.peek(1) == 'v') {
            this.finalizePredicates();
            cir.setReturnValue(this.getSelector());
        }
    }
}
