package com.github.thiagokokada.meowprinter.document

enum class CanvasTextSize(
    val sp: Int
) {
    SP6(6),
    SP8(8),
    SP10(10),
    SP12(12),
    SP14(14),
    SP16(16),
    SP18(18),
    SP20(20),
    SP24(24),
    SP28(28);

    val displayName: String
        get() = "${sp}sp"

    val previewSp: Float
        get() = (sp + 2).toFloat()

    val printSp: Float
        get() = sp.toFloat()

    companion object {
        fun fromStoredValue(value: String?): CanvasTextSize {
            return entries.firstOrNull { it.name == value } ?: SP12
        }
    }
}
