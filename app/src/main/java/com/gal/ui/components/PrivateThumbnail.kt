package com.gal.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.videoFramePercent

/** Decode private FileProvider videos directly, without ContentResolver.loadThumbnail. */
@Composable
fun PrivateThumbnail(uri: Uri?, isVideo: Boolean, description: String?, modifier: Modifier = Modifier, revision: Long = 0) {
    val context = LocalContext.current
    var failed by remember(uri, revision) { mutableStateOf(false) }
    var attempt by remember(uri, revision) { mutableStateOf(0) }
    val request = remember(uri, isVideo, revision, attempt) {
        ImageRequest.Builder(context).data(uri)
            .memoryCacheKey("$uri:$revision:preview-v2:$attempt")
            .apply {
                if (isVideo) {
                    videoFramePercent(if (attempt == 0) 0.1 else 0.0)
                    decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                }
            }.build()
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (uri == null || failed) Icon(
            if (isVideo) Icons.Outlined.PlayCircle else Icons.Outlined.Image,
            contentDescription = if (uri == null) "Álbum sem capa" else "Prévia indisponível",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (uri != null) AsyncImage(
            model = request,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            onError = { if (isVideo && attempt == 0) attempt = 1 else failed = true },
            onSuccess = { failed = false },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
