package com.gal.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.gal.model.Media
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun Modifier.dragSelection(
    state: LazyGridState,
    media: List<Media>,
    selected: Set<Long>,
    haptics: Boolean,
    toggle: (Media) -> Unit,
): Modifier = composed {
    val selection by rememberUpdatedState(selected)
    val onToggle by rememberUpdatedState(toggle)
    val feedback = LocalHapticFeedback.current
    val indices = remember(media) { media.mapIndexed { i, item -> item.id to i }.toMap() }
    pointerInput(state, media, haptics) {
        coroutineScope {
            var anchor = -1
            var pointer = Offset.Zero
            var baseline = emptySet<Long>()
            var applied = emptySet<Long>()
            fun hit(): Int? = state.layoutInfo.visibleItemsInfo.firstOrNull {
                pointer.x >= it.offset.x && pointer.x < it.offset.x + it.size.width &&
                    pointer.y >= it.offset.y && pointer.y < it.offset.y + it.size.height
            }?.key?.let { indices[it] }
            fun update() {
                val end = hit() ?: return
                if (anchor < 0) return
                val desired = baseline + (minOf(anchor, end)..maxOf(anchor, end)).map { media[it].id }
                ((desired - applied) + (applied - desired)).forEach { id ->
                    indices[id]?.let { onToggle(media[it]) }
                }
                applied = desired
            }
            val autoScroll = launch {
                while (true) {
                    delay(16)
                    if (anchor < 0) continue
                    val edge = 72.dp.toPx()
                    val speed = when {
                        pointer.y < edge -> -((edge - pointer.y) / edge).coerceIn(0f, 1f)
                        pointer.y > size.height - edge -> ((pointer.y - size.height + edge) / edge).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    if (speed != 0f) { state.scrollBy(speed * 18.dp.toPx()); update() }
                }
            }
            try {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        pointer = position
                        anchor = hit() ?: -1
                        baseline = selection.toSet()
                        applied = baseline
                        if (anchor >= 0) {
                            if (haptics) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            update()
                        }
                    },
                    onDrag = { change, _ ->
                        if (anchor >= 0) { change.consume(); pointer = change.position; update() }
                    },
                    onDragEnd = { anchor = -1 },
                    onDragCancel = { anchor = -1 },
                )
            } finally { autoScroll.cancel() }
        }
    }
}
