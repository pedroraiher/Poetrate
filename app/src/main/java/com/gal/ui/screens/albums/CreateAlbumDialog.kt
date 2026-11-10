package com.gal.ui.screens.albums

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp

@Composable
fun CreateAlbumDialog(busy: Boolean, error: String?, onDismiss: () -> Unit, onCreate: (String, Uri?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Novo álbum") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(100) }, enabled = !busy, label = { Text("Nome do álbum") }, singleLine = true)
                Text("Depois de adicionar fotos, segure o álbum para escolher uma delas como capa.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !busy && name.isNotBlank(), onClick = { onCreate(name.trim(), null) }) { Text(if (busy) "Criando…" else "Criar álbum") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } },
    )
}
