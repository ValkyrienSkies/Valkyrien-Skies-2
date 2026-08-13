package org.valkyrienskies.mod.fabric.mixin.feature.crash_report;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.crash_report.VSCrashReportHeader;

@Mixin(CrashReport.class)
public class CrashReportMixin {

    @Inject(method = "getFriendlyReport", at = @At(value = "INVOKE", target = "Ljava/lang/StringBuilder;append(Ljava/lang/String;)Ljava/lang/StringBuilder;", ordinal = 0, remap = false))
    private void addToCrashReportHeader(CallbackInfoReturnable<String> cir, @Local StringBuilder stringBuilder){
        VSCrashReportHeader.addCrashReportHeader(stringBuilder);
    }
}
