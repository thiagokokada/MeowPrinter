package com.github.thiagokokada.meowprinter.print

enum class PrinterProtocolProfile(
    val supportsBattery: Boolean,
    val supportsPaperMovement: Boolean
) {
    CAT(supportsBattery = false, supportsPaperMovement = true),
    MXW01(supportsBattery = true, supportsPaperMovement = false);

    companion object {
        // AE30 is shared by incompatible protocols. Only select tested models here;
        // do not infer MXW01 from a service UUID or enable untested V5X aliases.
        fun forAdvertisedName(name: String?): PrinterProtocolProfile =
            if (name?.trim().equals("MXW01", ignoreCase = true)) MXW01 else CAT
    }
}
