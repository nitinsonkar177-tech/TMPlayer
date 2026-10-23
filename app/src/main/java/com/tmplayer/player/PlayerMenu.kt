package com.tmplayer.player

import com.tmplayer.i18n.L

/**
 * One line of the television player's More menu. This build has no entries left for it: the
 * features the menu held (lock, sleep timer, repeat, bookmarks, downloads, casting into picture
 * in picture and the rest) are not part of the app, so the menu itself is gone and the enum is
 * empty on purpose.
 */
enum class PlayerMenuEntry

/**
 * One line of the phone's overflow menu. All that remains is the picture shape page, and only
 * while the bottom row has no room for its own button (a phone held upright), so the pinch is
 * not the only way there.
 */
enum class PhoneMenuEntry {
    PictureShape,
}

/** The line's text. [shape] names the fitting in force beside the page's name. */
fun PhoneMenuEntry.label(
    shape: VideoScale? = null,
): String = when (this) {
    PhoneMenuEntry.PictureShape -> shape?.let { L.commonWithDetail(L.playerPictureShape, it.label) } ?: L.playerPictureShape
}

/** Which lines the More menu offers on this device, in the order it draws them. */
object PlayerMenu {

    /** The television's More menu: nothing. The remote's row keeps its own buttons only. */
    fun tvEntries(): List<PlayerMenuEntry> = emptyList()

    /**
     * The phone's overflow: the picture shape page when the row has no button for it right now,
     * and nothing otherwise.
     */
    fun phoneEntries(pictureShape: Boolean = false): List<PhoneMenuEntry> = buildList {
        if (pictureShape) add(PhoneMenuEntry.PictureShape)
    }
}
