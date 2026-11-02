package org.valkyrienskies.mod.forge.mixin.feature.crash_report;

import net.minecraft.CrashReport;
import net.minecraftforge.logging.CrashReportExtender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.crash_report.VSCrashReportHeader;

@Mixin(CrashReportExtender.class)
public class CrashReportExtenderMixin {

    @Inject(method = "addCrashReportHeader", at = @At("HEAD"), remap = false)
    private static void addCrashReportHeader(StringBuilder stringbuilder, CrashReport crashReport, CallbackInfo ci) {
        VSCrashReportHeader.addCrashReportHeader(stringbuilder);
    }
}
