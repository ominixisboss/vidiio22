package com.ominix.vidiio.ui.player

/**
 * Spots rolling end credits from the picture itself: a mostly-black frame with a little light text
 * on it, held for several seconds. There is no credits marker in most files, so this reads the
 * video instead of guessing from the clock.
 *
 * Frames are tiny (see [WIDTH] x [HEIGHT]) downscaled copies, so the whole check is a few thousand
 * integer operations once a second.
 */
object CreditsDetector {
    const val WIDTH = 48
    const val HEIGHT = 27

    /** Fraction of pixels that must be near-black. */
    private const val MIN_DARK = 0.72f

    /** Fraction that must be lit: some text, but nowhere near a bright scene. */
    private const val MIN_LIT = 0.004f
    private const val MAX_LIT = 0.22f

    /** Per-channel luma thresholds out of 255. Text shrinks and blurs when downscaled, so "lit" is low. */
    private const val DARK_BELOW = 22
    private const val LIT_ABOVE = 70

    /** True when one frame looks like credits: dark background, a little text. [argb] is [WIDTH]x[HEIGHT]. */
    fun looksLikeCredits(argb: IntArray): Boolean {
        if (argb.isEmpty()) return false
        var dark = 0
        var lit = 0
        for (p in argb) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = (r * 299 + g * 587 + b * 114) / 1000
            if (luma < DARK_BELOW) dark++ else if (luma > LIT_ABOVE) lit++
        }
        val n = argb.size.toFloat()
        val darkFraction = dark / n
        val litFraction = lit / n
        return darkFraction >= MIN_DARK && litFraction in MIN_LIT..MAX_LIT
    }
}

/**
 * Turns a stream of per-frame verdicts into "the credits have started": most of the recent frames
 * must look like credits, so a dark scene or a fade does not trigger it.
 */
class CreditsTracker(private val window: Int = 6, private val required: Int = 5) {
    private val recent = ArrayDeque<Boolean>()

    /** Feed one verdict; returns true once the credits are considered to be running. */
    fun add(frameLooksLikeCredits: Boolean): Boolean {
        recent.addLast(frameLooksLikeCredits)
        while (recent.size > window) recent.removeFirst()
        return recent.size >= window && recent.count { it } >= required
    }

    fun reset() = recent.clear()
}
