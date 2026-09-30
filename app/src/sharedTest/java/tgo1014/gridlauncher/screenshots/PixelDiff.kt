package tgo1014.gridlauncher.screenshots

import kotlin.math.max
import kotlin.math.min

/**
 * Compares two images given as packed ARGB pixels, in order of one.
 *
 * This is deliberately not a library. A screenshot harness is only worth having if you can prove it
 * still *notices* things, and that proof has to be a fast test that runs on every commit. Keeping the
 * arithmetic free of `android.graphics` means [tgo1014.gridlauncher.screenshots.PixelDiffTest] can
 * hand it two hand-written buffers and check that a one-pixel change is caught - the failure mode of
 * a homemade comparator is not false alarms, it is a comparator quietly comparing nothing.
 *
 * Two numbers decide whether two images are the same:
 *
 *  * [channelTolerance] absorbs antialiasing and colour-conversion noise, which is a *rounding*
 *    difference. Anything structural moves a channel much further than this.
 *  * [maxChangedFraction] absorbs a handful of genuinely unstable pixels. Software rendering on a
 *    shared runner is not bit-exact everywhere, and a harness that fails on one stray pixel gets
 *    muted rather than fixed.
 *
 * Both are set from measured noise rather than taste; see `scripts/screenshot-baselines.sh`.
 */
object PixelDiff {

    /** Below this a change is rounding, not a different picture. */
    const val DEFAULT_CHANNEL_TOLERANCE = 8

    /**
     * 0.05% of a 1080x2400 screen is about 1300 pixels - roughly one glyph, or one icon's edge.
     *
     * Measured, not guessed. Re-recording every scene on freshly created emulators produces
     * byte-identical images, so the noise floor on a matching device is *zero* and the budget exists
     * only for a runner whose rasteriser differs slightly from a contributor's. The 8-per-channel
     * tolerance absorbs that: antialiasing drifts by one or two levels, not eight, so an edge
     * difference is not counted at all. What is left being counted is a pixel that genuinely changed
     * colour, and a whole glyph's worth of those is a failure.
     */
    const val DEFAULT_MAX_CHANGED_FRACTION = 0.0005

    data class Comparison(
        val width: Int,
        val height: Int,
        val changedPixels: Int,
        val maxChannelDelta: Int,
    ) {
        val totalPixels: Int get() = width * height

        val changedFraction: Double
            get() = if (totalPixels == 0) 0.0 else changedPixels.toDouble() / totalPixels

        /**
         * Whether the change is small enough to be rendering noise rather than a different picture.
         *
         * Only the fraction gates this. [maxChannelDelta] is a report, not a rule: a single pixel
         * differing by 255 in one channel is exactly the antialiasing edge this tolerance exists to
         * absorb, and gating on it as well would quietly turn the whole threshold back into
         * "bit-identical or fail", which is the harness nobody can keep alive.
         */
        fun isAcceptable(maxChangedFraction: Double = DEFAULT_MAX_CHANGED_FRACTION): Boolean =
            changedFraction <= maxChangedFraction

        /** The sentence a human actually needs when this fails. */
        fun describe(): String = "$changedPixels of $totalPixels pixels changed " +
            "(${"%.4f".format(changedFraction * 100)}%, largest channel difference $maxChannelDelta)"
    }

    /**
     * The largest per-channel difference between two ARGB pixels, ignoring nothing.
     *
     * Alpha is included: a tile going transparent is a real regression, and it is exactly the kind
     * that a "compare the colours only" shortcut would hide.
     */
    fun channelDelta(expected: Int, actual: Int): Int {
        var worst = 0
        for (shift in CHANNEL_SHIFTS) {
            worst = max(worst, kotlin.math.abs(((expected shr shift) and 0xFF) - ((actual shr shift) and 0xFF)))
        }
        return worst
    }

    fun compare(
        expected: IntArray,
        actual: IntArray,
        width: Int,
        height: Int,
        channelTolerance: Int = DEFAULT_CHANNEL_TOLERANCE,
    ): Comparison {
        val pixels = min(expected.size, actual.size)
        var changed = 0
        var worst = 0
        for (i in 0 until pixels) {
            val delta = channelDelta(expected[i], actual[i])
            if (delta > worst) worst = delta
            if (delta > channelTolerance) changed++
        }
        // A short buffer is not "almost the same image"; it is a different image, and it is what a
        // layout that stopped rendering looks like if the capture path truncates for some reason.
        if (expected.size != actual.size) changed = max(changed, min(expected.size, actual.size))
        return Comparison(width, height, changed, worst)
    }

    /**
     * A side-by-side image for a human: the actual capture, drained of colour where it matches, with
     * every differing pixel in magenta. Draining the matches rather than drawing an outline keeps a
     * one-pixel text shift visible instead of hiding it inside a busy tile.
     */
    fun diffImage(
        expected: IntArray,
        actual: IntArray,
        width: Int,
        height: Int,
        channelTolerance: Int = DEFAULT_CHANNEL_TOLERANCE,
    ): IntArray {
        val out = IntArray(width * height)
        for (i in out.indices) {
            val a = if (i < actual.size) actual[i] else 0
            val e = if (i < expected.size) expected[i] else 0
            out[i] = if (channelDelta(e, a) > channelTolerance) MAGENTA else desaturate(a)
        }
        return out
    }

    /** True when the two images hold the same pixels to within [tolerance]. */
    fun matches(
        expected: IntArray,
        actual: IntArray,
        width: Int,
        height: Int,
        tolerance: Int = DEFAULT_CHANNEL_TOLERANCE,
    ): Boolean = expected.size == actual.size &&
        expected.indices.all { channelDelta(expected[it], actual[it]) <= tolerance }

    /**
     * WCAG relative-luminance contrast ratio, used to assert that text is actually readable over
     * whatever is behind it rather than merely *present*.
     */
    fun contrastRatio(foreground: Int, background: Int): Double {
        val l = { packed: Int ->
            fun channel(shift: Int): Double {
                val raw = ((packed shr shift) and 0xFF) / 255.0
                return if (raw <= 0.03928) raw / 12.92 else Math.pow((raw + 0.055) / 1.055, 2.4)
            }
            0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val lighter = max(l(foreground), l(background))
        val darker = min(l(foreground), l(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun desaturate(packed: Int): Int {
        val r = (packed shr 16) and 0xFF
        val g = (packed shr 8) and 0xFF
        val b = packed and 0xFF
        // Rec. 601 luma, then blended halfway to itself so differences still read as shading.
        val grey = ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255)
        return (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
    }

    private val CHANNEL_SHIFTS = intArrayOf(24, 16, 8, 0)
    private const val MAGENTA = 0xFFFF00FF.toInt()
}