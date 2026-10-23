package com.tmplayer.player

import androidx.media3.common.Player

/**
 * What the audio track picker needs from the screen playing the video: the player and the sound
 * timing. The player activity is the one host.
 */
interface TrackPickerHost {
    val player: Player?

    fun syncDelaysNow(): SyncDelays

    fun stepDelay(trackType: Int, direction: Int)
}
