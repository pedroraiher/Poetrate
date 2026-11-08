package com.gal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel

/** A conflated queue keeps scrubbing responsive without accumulating scroll jobs. */
@Composable
internal fun FastScrollGrid(
    modifier: Modifier,
    bottomInset: Dp,
    content: @Composable (LazyGridState) -> Unit,
) {
    val state = rememberLazyGridState()
    val targets = remember { Channel<Float>(Channel.CONFLATED) }
    LaunchedEffect(state) {
        for (fraction in targets) {
            val total = state.layoutInfo.totalItemsCount
            if (total > 0) state.scrollToItem(((total - 1) * fraction).toInt())
        }
    }
    DisposableEffect(targets) { onDispose { targets.close() } }
    val color = MaterialTheme.colorScheme.primary
    Box(modifier) {
        content(state)
        if (state.canScrollForward || state.canScrollBackward) {
            Canvas(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    .padding(top = 12.dp, bottom = bottomInset + 88.dp)
                    .width(32.dp)
                    .pointerInput(state) {
                        fun seek(y: Float) {
                            val thumb = 48.dp.toPx().coerceAtMost(size.height.toFloat())
                            targets.trySend(((y - thumb / 2) / (size.height - thumb).coerceAtLeast(1f)).coerceIn(0f, 1f))
                        }
                        detectDragGestures(
                            onDragStart = { seek(it.y) },
                            onDrag = { change, _ -> change.consume(); seek(change.position.y) },
                        )
                    }
            ) {
                val thumb = 48.dp.toPx().coerceAtMost(size.height)
                val total = state.layoutInfo.totalItemsCount
                val fraction = if (!state.canScrollForward) 1f else
                    state.firstVisibleItemIndex.toFloat() / (total - 1).coerceAtLeast(1)
                val x = size.width - 10.dp.toPx()
                drawRoundRect(color.copy(alpha = .15f), Offset(x, 0f), Size(4.dp.toPx(), size.height), CornerRadius(4.dp.toPx()))
                drawRoundRect(color, Offset(x - 2.dp.toPx(), fraction * (size.height - thumb)), Size(8.dp.toPx(), thumb), CornerRadius(4.dp.toPx()))
            }
        }
    }
}
