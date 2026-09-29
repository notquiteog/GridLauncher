package tgo1014.gridlauncher.ui.theme

/** Spoken names for the accent swatches, so a screen reader does not read raw hex. */
fun accentName(color: Long): String = when (color) {
    0xFF0078D7 -> "Blue"
    0xFF008A00 -> "Green"
    0xFFB4009E -> "Magenta"
    0xFFD24726 -> "Orange"
    0xFF643EBF -> "Violet"
    0xFF006D77 -> "Teal"
    0xFF1C1C1C -> "Black"
    else -> "Accent"
}
