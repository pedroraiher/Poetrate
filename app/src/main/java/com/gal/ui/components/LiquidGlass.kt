package com.gal.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.*
import androidx.compose.ui.unit.dp

class GlassBackdrop(val layer: GraphicsLayer) {
    var available by mutableStateOf(false)
    var recording = false
    var origin by mutableStateOf(Offset.Zero)
}
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

@Composable
fun Modifier.glassSource(): Modifier {
    val source = LocalGlassBackdrop.current ?: return this
    DisposableEffect(source) { onDispose { source.available = false } }
    return captureGlass(source)
}

fun Modifier.captureGlass(source: GlassBackdrop) = onGloballyPositioned { source.origin = it.positionInRoot() }.drawWithContent {
    source.available = true
    source.recording = true
    try { source.layer.record { this@drawWithContent.drawContent() } }
    finally { source.recording = false }
    drawLayer(source.layer)
}

/** Samples the actual live scene. No bitmap readback and no blur pass. */
@Composable
fun LiquidGlass(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val source = LocalGlassBackdrop.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shader = remember { RuntimeShader("""
        uniform shader scene;
        uniform float2 bounds;
        uniform float density;
        half4 main(float2 p) {
            float2 halfSize = bounds * .5;
            float r = min(halfSize.x, halfSize.y);
            float2 q = abs(p-halfSize) - (halfSize-r);
            float d = length(max(q,0.0)) + min(max(q.x,q.y),0.0) - r;
            float edge = 1.0 - smoothstep(0.0, min(22.0*density,r*.82), -d);
            float2 centerline = clamp(p, float2(r), bounds-float2(r));
            float2 normal = (p-centerline)/max(length(p-centerline),1.0);
            // Thick clear lens: a wide refractive shoulder, measured in dp.
            float bend = edge*edge*(3.0-2.0*edge);
            float2 lens = (p-halfSize) * (0.11*bend);
            float2 uv = clamp(p-normal*bend*14.0*density-lens, float2(0.5), bounds-float2(0.5));
            return scene.eval(uv);
        }
    """) }
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }.clip(CircleShape)
        .drawWithContent { if (source?.recording != true) drawContent() }) {
        Canvas(Modifier.matchParentSize().graphicsLayer {
            clip = true
            compositingStrategy = CompositingStrategy.Offscreen
            if (size.width > 0 && size.height > 0) {
                shader.setFloatUniform("bounds", size.width, size.height)
                shader.setFloatUniform("density", density)
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "scene").asComposeRenderEffect()
            }
        }) {
            if (source != null && source.available && !source.recording) {
                val delta = source.origin-origin
                translate(delta.x,delta.y) { drawLayer(source.layer) }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha=.72f),Color.White.copy(alpha=.08f),Color(0xFFC9954E).copy(alpha=.35f))), cornerRadius=androidx.compose.ui.geometry.CornerRadius(size.height/2),style=Stroke(1.2.dp.toPx()))
        }
        content()
    }
}

/** Dual-tone edges keep symbols readable over both dark and bright photos. */
@Composable
fun JewelIcon(icon: ImageVector, description: String?, modifier: Modifier = Modifier, selected: Boolean = false) {
    val iconSize = if (selected) 31.dp else 28.dp
    val offsets = remember { listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1) }
    Box(modifier.size(iconSize)) {
        offsets.forEach { (x,y) ->
            Icon(icon, null, tint = Color(0xFF142235), modifier = Modifier.matchParentSize().offset((x*1.25f).dp,(y*1.25f).dp))
        }
        Icon(icon, description, tint = if(selected) Color(0xFFFFD590) else Color(0xFFFFF7E7), modifier = Modifier.matchParentSize())
    }
}
