package com.tmplayer.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.MaterialTheme as M3Theme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tmplayer.i18n.L
import com.tmplayer.i18n.Translator
import com.tmplayer.ui.components.ChoiceLine
import com.tmplayer.ui.components.ChoiceColumns
import com.tmplayer.ui.components.ChoiceList
import com.tmplayer.ui.components.ChoiceSection
import com.tmplayer.ui.components.ChoiceSheet
import com.tmplayer.ui.components.FocusOnOpen
import com.tmplayer.ui.components.LocalChoiceWide
import com.tmplayer.ui.components.isTouch
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.TMPlayerTheme
import com.tmplayer.ui.theme.Tone
import java.util.Locale

/**
 * The audio track chooser, drawn as the language picker's modal ([ChoiceSheet]) over the dimmed
 * video on a phone and a television alike.
 *
 * Choosing a track acts on the one tap or press and closes. Below the tracks sit the timing lines
 * (earlier, later, reset). Those act in place and leave the picker open where it was scrolled to,
 * so a line can be nudged a tenth at a time while the sound plays behind the dimmed list.
 *
 * A fragment on the activity's back stack, so the player knows the picker is up ([isOpen]) and
 * holds its controls while it is. The panel itself is a window of its own (a dialog), which takes
 * the remote's keys away from the player while it is up: nothing behind it seeks or pauses.
 */
@UnstableApi
class TrackPickerFragment : Fragment() {

    private val host: TrackPickerHost? get() = activity as? TrackPickerHost

    /** A track to choose, its name and, under it, what tells it apart from a namesake. */
    private class Option(val group: Tracks.Group?, val trackIndex: Int, val label: String, val detail: String? = null)

    /** The groups the lines fall into, in order, with the heading each one carries. */
    private enum class Group(val heading: () -> String?) {
        Tracks({ null }),
        Timing({ L.tracksSectionTiming }),
    }

    /** One line of the panel, drawn as the phone's or the television's row. */
    private class Line(
        val key: Any,
        val title: String,
        val detail: String? = null,
        val selected: Boolean? = null,
        val icon: ImageVector? = null,
        val enabled: Boolean = true,
        val group: Group = Group.Tracks,
        val onClick: () -> Unit,
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // Not named Content: inside apply that resolves to ComposeView.Content, which draws
            // this same lambda again, and the player died of a stack overflow on every TV.
            setContent { TMPlayerTheme { PickerPanel() } }
        }

    /** Closes the picker. */
    private fun close() {
        val fm = parentFragmentManager
        if (isAdded && !fm.isStateSaved) fm.popBackStack(BACK_STACK, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    @Composable
    private fun PickerPanel() {
        val touch = isTouch()
        val closeFocus = remember { FocusRequester() }
        val firstFocus = remember { FocusRequester() }
        // Bumped by every in-place line, so the timing figures under them redraw.
        var tick by remember { mutableIntStateOf(0) }
        ChoiceSheet(
            title = L.tracksAudioLanguage,
            onDismiss = ::close,
            onClose = ::close,
            closeFocus = closeFocus,
            ignoreRelease = !touch,
            fullScreen = true,
        ) {
            @Suppress("UNUSED_EXPRESSION") tick
            val lines = trackLines(onInPlace = { tick++ })
            Lines(
                lines,
                firstFocus,
                focusKey = lines.firstOrNull { it.selected == true }?.key ?: lines.firstOrNull()?.key,
                onInPlace = { tick++ },
                version = tick,
            )
            // Inside the dialog's own composition: asked from outside, the request came before
            // the lines existed, and the window's own focus fell on the first line instead of
            // the track playing.
            FocusOnOpen(firstFocus)
        }
    }

    // ---- the tracks ---------------------------------------------------------------------------

    private fun trackLines(onInPlace: () -> Unit): List<Line> {
        val host = host ?: return emptyList()
        val player = host.player ?: return emptyList()
        val options = buildList {
            var number = 0
            for (group in player.currentTracks.groups) {
                if (group.type != C.TRACK_TYPE_AUDIO) continue
                for (index in 0 until group.length) {
                    if (!group.isTrackSupported(index)) continue
                    number++
                    val format = group.getTrackFormat(index)
                    add(Option(group, index, describe(group, index, number - 1), tags(format).ifEmpty { null }?.joinToString("  ·  ")))
                }
            }
        }.let(::numberNamesakes)
        return buildList {
            if (options.isEmpty()) {
                add(Line("none", L.tracksNoOtherAudio) { close() })
                // Sound can still be out of step with only the one track to choose from.
                addAll(timingLines(host, onInPlace))
                return@buildList
            }
            options.forEachIndexed { index, option ->
                val selected = isSelected(player.currentTracks, option)
                add(
                    Line(
                        key = "track$index",
                        title = option.label,
                        detail = listOfNotNull(if (selected) L.tracksPlayingNow else null, option.detail)
                            .ifEmpty { null }?.joinToString("  ·  "),
                        selected = selected,
                    ) { choose(option) },
                )
            }
            addAll(timingLines(host, onInPlace))
        }
    }

    /** The offset now in force, under each timing line, so a press shows where it got to. */
    private fun timingLines(host: TrackPickerHost, onInPlace: () -> Unit): List<Line> {
        val ms = host.syncDelaysNow().audioMs
        val step = L.tracksDelayStep(SyncDelays.label(ms))
        fun nudge(direction: Int) {
            host.stepDelay(C.TRACK_TYPE_AUDIO, direction)
            onInPlace()
        }
        return listOf(
            Line("earlier", L.tracksSoundEarlier, step, icon = TmIcons.ChevronLeft, group = Group.Timing) { nudge(-1) },
            Line("later", L.tracksSoundLater, step, icon = TmIcons.ChevronRight, group = Group.Timing) { nudge(1) },
            // Greys itself out at zero, but keeps the remote: the row stays focusable.
            Line(
                key = "reset",
                title = L.tracksResetTiming,
                detail = if (ms == 0L) L.tracksDelayNone else L.tracksDelayNow(SyncDelays.label(ms)),
                icon = TmIcons.Refresh,
                enabled = ms != 0L,
                group = Group.Timing,
            ) { nudge(0) },
        )
    }

    private fun choose(option: Option) {
        val player = host?.player
        if (player != null && option.group != null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
                .build()
        }
        close()
    }

    // ---- drawing ------------------------------------------------------------------------------

    /**
     * [lines] in a column that scrolls inside the panel ([ChoiceList]); the one keyed [focusKey]
     * carries [firstFocus]. A Column rather than a lazy list, so a refresh after an in-place line
     * keeps the scroll where the viewer left it and the focused line is always composed.
     *
     * The timing group opens with its heading.
     */
    @Composable
    private fun androidx.compose.foundation.layout.ColumnScope.Lines(
        lines: List<Line>,
        firstFocus: FocusRequester,
        focusKey: Any?,
        onInPlace: (() -> Unit)? = null,
        /** Bumped by every in-place change, so the touch controls redraw what they read from the host. */
        version: Int = 0,
    ) {
        val (choose, adjust) = lines.partition { it.group == Group.Tracks }
        // On a touch screen the timing is a control rather than lines: one stepper for Earlier
        // and Later, with the figure and Reset between them.
        val touchAdjust: (@Composable () -> Unit)? = if (isTouch() && onInPlace != null && adjust.isNotEmpty()) {
            { TouchAdjust(version, onInPlace) }
        } else {
            null
        }
        // A landscape phone sets the tracks beside the timing, each half scrolling on its own,
        // rather than one long list three lines of it at a time.
        if (LocalChoiceWide.current && choose.isNotEmpty() && adjust.isNotEmpty()) {
            ChoiceColumns(
                start = { LineRows(choose, firstFocus, focusKey) },
                end = { if (touchAdjust != null) touchAdjust() else LineRows(adjust, firstFocus, focusKey) },
            )
        } else if (touchAdjust != null) {
            ChoiceList {
                LineRows(choose, firstFocus, focusKey)
                touchAdjust()
            }
        } else {
            ChoiceList { LineRows(lines, firstFocus, focusKey) }
        }
    }

    /**
     * The timing as a touch screen draws it. Each change acts at once on the video behind and
     * redraws the figure here through [onInPlace].
     */
    @Composable
    private fun TouchAdjust(version: Int, onInPlace: () -> Unit) {
        val host = host ?: return
        // What the host holds is not Compose state: reading [version] is what redraws the figure
        // after a press, rather than only when the picker opens again.
        @Suppress("UNUSED_EXPRESSION") version
        ChoiceSection(L.tracksSectionTiming)
        val ms = host.syncDelaysNow().audioMs
        fun nudge(direction: Int) {
            host.stepDelay(C.TRACK_TYPE_AUDIO, direction)
            onInPlace()
        }
        // Earlier and Later either side of where the timing stands now, so a press shows its
        // answer between the two buttons; Reset sits under the figure once there is one.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ADJUST_PAD).padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { nudge(-1) }, modifier = Modifier.weight(1f).height(48.dp)) {
                M3Icon(TmIcons.ChevronLeft, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                M3Text(L.tracksEarlier, maxLines = 1)
            }
            androidx.compose.foundation.layout.Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                M3Text(SyncDelays.label(ms), style = M3Theme.typography.titleLarge, color = Tone.text, maxLines = 1)
                if (ms != 0L) {
                    TextButton(onClick = { nudge(0) }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        M3Text(L.tracksResetTiming, maxLines = 1)
                    }
                } else {
                    M3Text(L.tracksDelayNone, style = M3Theme.typography.bodySmall, color = Tone.muted, maxLines = 2, textAlign = TextAlign.Center)
                }
            }
            OutlinedButton(onClick = { nudge(1) }, modifier = Modifier.weight(1f).height(48.dp)) {
                M3Text(L.tracksLater, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                M3Icon(TmIcons.ChevronRight, contentDescription = null)
            }
        }
    }

    /** [lines] one under another, each group after the first opened by its heading. */
    @Composable
    private fun LineRows(lines: List<Line>, firstFocus: FocusRequester, focusKey: Any?) {
        lines.forEachIndexed { index, line ->
            val heading = line.group.heading()
            // A column that starts on Timing (the right half of a landscape phone) still names it.
            if (index == 0 && heading != null) {
                ChoiceSection(heading)
            } else if (index > 0 && lines[index - 1].group != line.group) {
                if (heading != null) ChoiceSection(heading) else Spacer(Modifier.height(GROUP_GAP))
            }
            ChoiceLine(
                title = line.title,
                detail = line.detail,
                selected = line.selected,
                icon = line.icon,
                enabled = line.enabled,
                modifier = if (line.key == focusKey) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = line.onClick,
            )
        }
    }

    // ---- naming the tracks --------------------------------------------------------------------

    private fun isSelected(tracks: Tracks, option: Option): Boolean {
        if (option.group == null) {
            return tracks.groups.none { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
        }
        return option.group.isTrackSelected(option.trackIndex)
    }

    /** Language first, then sound format and channel layout: what tells dual audio apart. */
    private fun describe(group: Tracks.Group, index: Int, position: Int): String {
        val format = group.getTrackFormat(index)
        val language = format.language
            ?.takeIf { it.isNotBlank() && it != "und" }
            ?.let { Translator.messages.formatter.trackLanguage(it) ?: it }
        val label = format.label?.takeIf { it.isNotBlank() }

        val name = when {
            label != null && language != null && !label.contains(language, ignoreCase = true) ->
                "$language ($label)"
            label != null -> label
            language != null -> language
            else -> L.tracksTrackNumber((position + 1).toString())
        }

        val details = buildList {
            soundFormat(format)?.let { add(it) }
            if (format.channelCount > 0) add(channels(format.channelCount))
        }

        return if (details.isEmpty()) name else "$name  ·  ${details.joinToString(" ")}"
    }

    /** The track language's script or region tag (Simplified, Traditional, Brazil), when there is one. */
    private fun tags(format: Format): List<String> =
        listOfNotNull(Translator.messages.formatter.trackLanguageVariant(format.language))

    /**
     * Tracks that still read the same, name and tags alike, are told apart by their number
     * among the tracks of their kind, as a last resort.
     */
    private fun numberNamesakes(options: List<Option>): List<Option> {
        val seen = options.groupingBy { it.label to it.detail }.eachCount()
        var number = 0
        return options.map { option ->
            if (option.group == null) return@map option
            number++
            if ((seen[option.label to option.detail] ?: 0) < 2) return@map option
            val tag = L.tracksTrackNumber(number.toString())
            Option(option.group, option.trackIndex, option.label, listOfNotNull(option.detail, tag).joinToString("  ·  "))
        }
    }

    /**
     * The two sound-format names a viewer might recognise, and nothing else. Anything outside the
     * list is left out: the channel layout beside it already identifies the track.
     */
    private fun soundFormat(format: Format): String? {
        val id = "${format.codecs.orEmpty()} ${format.sampleMimeType.orEmpty()}".lowercase(Locale.ROOT)
        return when {
            DOLBY.any { id.contains(it) } -> "Dolby"
            DTS.any { id.contains(it) } -> "DTS"
            else -> null
        }
    }

    private fun channels(count: Int) = when (count) {
        1 -> L.tracksMono
        2 -> L.tracksStereo
        6 -> "5.1"
        8 -> "7.1"
        else -> L.tracksChannels(count.toString())
    }

    companion object {
        /** The room that opens a group without a heading: about what a heading's top takes. */
        private val GROUP_GAP = 8.dp

        /** The touch controls' inset, the same 24 dp the lines carry, so they line up with the radios. */
        private val ADJUST_PAD = 24.dp
        private const val TAG = "track_picker"
        private const val BACK_STACK = "track_picker"

        // Spelled several ways depending on whether the id came from the MP4 sample entry or
        // from the MIME type, so both are matched.
        private val DOLBY = listOf("ac-3", "ac3", "ec-3", "eac3")
        private val DTS = listOf("dts", "dca")

        /** Whether the picker is up. */
        fun isOpen(fm: FragmentManager): Boolean = fm.findFragmentByTag(TAG) != null

        /** Opens the audio track picker over [container]. */
        fun show(fm: FragmentManager, container: Int) {
            if (isOpen(fm) || fm.isStateSaved) return
            val picker = TrackPickerFragment()
            fm.beginTransaction().add(container, picker, TAG).addToBackStack(BACK_STACK).commit()
        }
    }
}
