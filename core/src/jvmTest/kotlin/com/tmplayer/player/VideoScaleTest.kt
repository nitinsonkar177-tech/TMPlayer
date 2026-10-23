package com.tmplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoScaleTest {

    @Test
    fun `the cycle is two stops long and comes back to normal`() {
        assertEquals(VideoScale.Crop, VideoScale.Fit.next())
        assertEquals(VideoScale.Fit, VideoScale.Crop.next())
    }

    @Test
    fun `the resize modes are the ones Media3 understands`() {
        // RESIZE_MODE_FIT and RESIZE_MODE_ZOOM respectively.
        assertEquals(0, VideoScale.Fit.resizeMode)
        assertEquals(4, VideoScale.Crop.resizeMode)
    }
}
