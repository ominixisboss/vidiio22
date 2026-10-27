package com.ominix.vidiio.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsDetectorTest {
    private val n = CreditsDetector.WIDTH * CreditsDetector.HEIGHT
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun frame(background: Int, textPixels: Int, textValue: Int = 200): IntArray =
        IntArray(n) { if (it < textPixels) gray(textValue) else gray(background) }

    @Test fun `black with some text is credits`() = assertTrue(CreditsDetector.looksLikeCredits(frame(4, 60)))

    @Test fun `all black is not credits (a fade or a gap)`() = assertFalse(CreditsDetector.looksLikeCredits(frame(0, 0)))

    @Test fun `a bright scene is not credits`() = assertFalse(CreditsDetector.looksLikeCredits(frame(150, 0)))

    @Test fun `a dim but lit scene is not credits`() = assertFalse(CreditsDetector.looksLikeCredits(frame(40, 0)))

    @Test fun `too much light is a scene, not text`() = assertFalse(CreditsDetector.looksLikeCredits(frame(4, n / 2)))

    @Test fun `empty frame is not credits`() = assertFalse(CreditsDetector.looksLikeCredits(IntArray(0)))

    @Test fun `tracker needs sustained credits, not a blip`() {
        val t = CreditsTracker()
        repeat(2) { t.add(true) }
        repeat(3) { assertFalse(t.add(false)) }
        t.reset()
        var started = false
        repeat(6) { started = t.add(true) }
        assertTrue(started)
    }

    @Test fun `one bad frame in a run does not cancel it`() {
        val t = CreditsTracker()
        val verdicts = listOf(true, true, false, true, true, true)
        var started = false
        verdicts.forEach { started = t.add(it) }
        assertTrue(started)
    }
}
