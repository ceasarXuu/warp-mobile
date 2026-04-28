package dev.warp.mobile.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalKeyboardStateTest {
    private val viewportWidth = 100f
    private val sideBandWidth = 42f

    @Test
    fun horizontalDragMovesBetweenAstropathAnchors() {
        assertEquals(
            KeyboardBandAnchor.RightPeek,
            KeyboardBandAnchor.Center.resolveAfterDrag(
                startOffsetPx = sideBandWidth,
                endOffsetPx = 62f,
                viewportWidthPx = viewportWidth,
                sideBandWidthPx = sideBandWidth,
            ),
        )
        assertEquals(
            KeyboardBandAnchor.LeftPeek,
            KeyboardBandAnchor.Center.resolveAfterDrag(
                startOffsetPx = sideBandWidth,
                endOffsetPx = 22f,
                viewportWidthPx = viewportWidth,
                sideBandWidthPx = sideBandWidth,
            ),
        )
        assertEquals(
            KeyboardBandAnchor.Center,
            KeyboardBandAnchor.Center.resolveAfterDrag(
                startOffsetPx = sideBandWidth,
                endOffsetPx = 55f,
                viewportWidthPx = viewportWidth,
                sideBandWidthPx = sideBandWidth,
            ),
        )
    }

    @Test
    fun horizontalDragClampsAtEdgeAnchors() {
        assertEquals(
            KeyboardBandAnchor.LeftPeek,
            KeyboardBandAnchor.LeftPeek.resolveAfterDrag(
                startOffsetPx = 0f,
                endOffsetPx = 0f,
                viewportWidthPx = viewportWidth,
                sideBandWidthPx = sideBandWidth,
            ),
        )
        assertEquals(
            KeyboardBandAnchor.RightPeek,
            KeyboardBandAnchor.RightPeek.resolveAfterDrag(
                startOffsetPx = sideBandWidth * 2f,
                endOffsetPx = sideBandWidth * 2f,
                viewportWidthPx = viewportWidth,
                sideBandWidthPx = sideBandWidth,
            ),
        )
    }

    @Test
    fun longDragCanSkipToDirectionalClosestAnchor() {
        assertEquals(
            KeyboardBandAnchor.RightPeek,
            KeyboardBandAnchor.LeftPeek.resolveAfterDrag(
                startOffsetPx = 0f,
                endOffsetPx = sideBandWidth * 2f,
                viewportWidthPx = viewportWidth,
                sideBandWidthPx = sideBandWidth,
            ),
        )
        assertEquals(
            KeyboardBandAnchor.LeftPeek,
            KeyboardBandAnchor.RightPeek.resolveAfterDrag(
                startOffsetPx = sideBandWidth * 2f,
                endOffsetPx = 0f,
                viewportWidthPx = viewportWidth,
                sideBandWidthPx = sideBandWidth,
            ),
        )
    }
}
