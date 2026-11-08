package com.gal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel

@Composable
internal fun FastScrollList(
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    val state=rememberLazyListState()
    val requests=remember{Channel<Float>(Channel.CONFLATED)}
    LaunchedEffect(state){for(fraction in requests){val count=state.layoutInfo.totalItemsCount;if(count>0)state.scrollToItem(((count-1)*fraction).toInt())}}
    DisposableEffect(requests){onDispose{requests.close()}}
    val color=MaterialTheme.colorScheme.primary
    Box(modifier){
        if(horizontal)LazyRow(state=state,modifier=Modifier.fillMaxWidth().padding(bottom=24.dp),contentPadding=contentPadding,horizontalArrangement=Arrangement.spacedBy(8.dp),content=content)
        else LazyColumn(state=state,modifier=Modifier.fillMaxSize().padding(end=20.dp),contentPadding=contentPadding,verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        if(state.canScrollBackward||state.canScrollForward){
            val bar=if(horizontal)Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp)
                else Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp)
            Canvas(bar.pointerInput(state,horizontal){
                fun seek(position:Offset){val length=if(horizontal)size.width.toFloat() else size.height.toFloat();val thumb=48.dp.toPx().coerceAtMost(length);val at=if(horizontal)position.x else position.y;requests.trySend(((at-thumb/2)/(length-thumb).coerceAtLeast(1f)).coerceIn(0f,1f))}
                detectDragGestures(onDragStart={seek(it)},onDrag={change,_->change.consume();seek(change.position)})
            }){
                val length=if(horizontal)size.width else size.height
                val thumb=48.dp.toPx().coerceAtMost(length)
                val fraction=if(!state.canScrollForward)1f else state.firstVisibleItemIndex.toFloat()/(state.layoutInfo.totalItemsCount-1).coerceAtLeast(1)
                val offset=fraction*(length-thumb)
                val track=if(horizontal)Size(length,4.dp.toPx()) else Size(4.dp.toPx(),length)
                val trackOrigin=if(horizontal)Offset(0f,size.height/2) else Offset(size.width/2,0f)
                drawRoundRect(color.copy(alpha=.18f),trackOrigin,track,CornerRadius(4.dp.toPx()))
                val handle=if(horizontal)Size(thumb,8.dp.toPx()) else Size(8.dp.toPx(),thumb)
                val origin=if(horizontal)Offset(offset,size.height/2-2.dp.toPx()) else Offset(size.width/2-2.dp.toPx(),offset)
                drawRoundRect(color,origin,handle,CornerRadius(4.dp.toPx()))
            }
        }
    }
}
