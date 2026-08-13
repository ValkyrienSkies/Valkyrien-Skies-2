package org.valkyrienskies.mod.common.crash_report

import net.minecraft.Util

object VSCrashReportHeader {

    val HEADER_COMMENTS: List<String> = listOf(
        "wait",
        "optimized so hard it's like the game isn't even running",
        "live krunch reaction",
        "now, I know that sounds bad",
        "high quality free range artisanal organic crash",
        "everything is not opti-fine",
        "did I break the GC again?",
        "bropeller",
        "mod doesnt work... devs fix you're mod... smh...",
        "they put the mamsnrhbr chehfde in the soder",
        "did you try turning it off and on again?",
        "are you sure?",
        "huh? whuh?",
        "also try Mimicrune!",
        "okay wait no I know this one- er... wait no hang on uhh no wait how did you even- whuh",
        "never seen that one before...",
        "500 mixins",
        "your're*",
        "why isn't the ram usage going down",
        "how download more ram",
        "krunchitized",
        "successful pilgrimage to the shipyard",
        "perchanceably",
        "'I did a quick test, it seemed alright.'",
        "lemme see, and then we carry the one. yup. and then add the remainder, and there we go. your ship is yeeted."
    )

    private fun getHeaderComment(): String {
        return try {
            HEADER_COMMENTS[(Util.getMillis() % HEADER_COMMENTS.size).toInt()]
        } catch (_: Exception) {
            "...oh dear."
        }
    }

    @JvmStatic
    fun addCrashReportHeader(builder: StringBuilder) {
        builder.append("\n\n// " + getHeaderComment())
        builder.append("\nPlease check that this issue occurs without Valkyrien Skies before reporting it to other mod authors.")
        builder.append("\nIf this issue does not occur without Valkyrien Skies, please check if your issue has already been reported at https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues?q=is%3Aissue.")
        builder.append("\nIf your issue has not been reported or you are unsure, please report it at the link provided above.\n\n")
    }

}
