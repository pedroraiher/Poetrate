package com.gal.ui.screens.albums

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gal.ui.components.PrivateThumbnail

@Composable
fun CreateAlbumDialog(busy: Boolean, error: String?, onDismiss: () -> Unit, onCreate: (String, Uri?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var cover by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    fun release(uri: String?) {
        uri?.let { runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { selected ->
        if (selected != null) {
            if (cover != selected.toString()) release(cover)
            runCatching { context.contentResolver.takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            cover = selected.toString()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) { release(cover); onDismiss() } },
        title = { Text("Novo álbum") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(100) }, enabled = !busy,
                    label = { Text("Nome do álbum") }, singleLine = true,
                )
                PrivateThumbnail(cover?.let(Uri::parse), false, "Capa do álbum", Modifier.fillMaxWidth().height(130.dp))
                TextButton(enabled = !busy, onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(if (cover == null) "Escolher capa" else "Trocar capa") }
                if (cover != null) TextButton(enabled = !busy, onClick = { release(cover); cover = null }) { Text("Remover capa") }
                Text("A capa é opcional. O álbum pode começar vazio.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank(), onClick = { onCreate(name.trim(), cover?.let(Uri::parse)) }) {
                Text(if (busy) "Criando…" else "Criar álbum")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { release(cover); onDismiss() }) { Text("Cancelar") } },
    )
}
