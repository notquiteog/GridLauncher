package tgo1014.gridlauncher.data

/** The most common opaque outer-edge color; transparent padding and icon centers do not vote. */
internal fun iconEdgeColor(pixels: IntArray, width: Int, height: Int): Long? {
    require(width > 0 && height > 0 && pixels.size == width * height)
    val edge = mutableListOf<Int>()
    fun sample(indices: IntProgression) {
        indices.firstOrNull { (pixels[it] ushr 24) >= 240 }?.let { edge += pixels[it] }
    }
    for (y in 0 until height) {
        sample(y * width until (y + 1) * width)
        sample((y + 1) * width - 1 downTo y * width)
    }
    for (x in 0 until width) {
        sample(x until pixels.size step width)
        sample((height - 1) * width + x downTo x step width)
    }
    // Group small antialiasing/gradient differences without averaging different edge colors.
    val dominant = edge.groupBy { (it and 0x00F00000) or (it and 0x0000F000) or (it and 0x000000F0) }
        .maxByOrNull { it.value.size }?.value ?: return null
    fun channel(shift: Int) = dominant.sumOf { (it ushr shift) and 255 } / dominant.size
    return 0xFF000000L or (channel(16).toLong() shl 16) or (channel(8).toLong() shl 8) or channel(0).toLong()
}
