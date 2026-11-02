package com.gal.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gal.data.local.PrivateMediaStore
import com.gal.ui.navigation.LocalFloatingNavBarPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AddMenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** Shared theme token keeps both + buttons consistent and ready for future color settings. */
@Composable
fun AddMenuFab(actions: List<AddMenuAction>, busy: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(bottom = LocalFloatingNavBarPadding.current + 8.dp)) {
        FloatingActionButton(
            onClick = { if (!busy) expanded = !expanded },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(Icons.Filled.Add, contentDescription = "Adicionar")
        }
        DropdownMenu(expanded = expanded && !busy, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    onClick = { expanded = false; action.onClick() },
                )
            }
        }
    }
}

@HiltViewModel
class LibraryAddViewModel @Inject constructor(
    private val store: PrivateMediaStore,
    private val saved: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    fun prepareCapture(video: Boolean, album: String?, launch: (Intent) -> Unit) {
        if (busy.value || saved.get<String>("capture_name") != null) return
        viewModelScope.launch {
            busy.value = true
            try {
                val file = withContext(Dispatchers.IO) { store.newCapture(video) }
                saved["capture_name"] = file.name
                saved["capture_video"] = video
                saved["capture_album"] = album ?: "Capturas"
                val output = store.uri(file)
                val intent = Intent(if (video) MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, output)
                    clipData = ClipData.newRawUri("Captura Poetrate", output)
                    addFlags(grants)
                    if (video) putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                }
                launch(intent)
            } catch (error: Exception) {
                clearPending()
                message.value = "Não foi possível abrir a câmera do celular. Verifique se há um app de câmera instalado."
            } finally { busy.value = false }
        }
    }

    fun completeCapture(resultCode: Int, returned: Uri?) {
        val name = saved.get<String>("capture_name") ?: return
        val video = saved.get<Boolean>("capture_video") ?: false
        val album = saved.get<String>("capture_album") ?: "Capturas"
        viewModelScope.launch {
            busy.value = true
            try {
                if (resultCode == Activity.RESULT_OK) {
                    withContext(Dispatchers.IO) {
                        val file = store.pendingFile(name)
                        if (file.length() > 0) {
                            store.commit(file, if (video) "video/mp4" else "image/jpeg",
                                "${if (video) "VID" else "IMG"}_${System.currentTimeMillis()}.${if (video) "mp4" else "jpg"}", album)
                        } else if (returned != null && returned != store.uri(file)) {
                            // Some OEM cameras return a content URI instead of filling EXTRA_OUTPUT.
                            store.importUri(returned, album)
                        } else error("A câmera não retornou o arquivo completo.")
                    }
                    message.value = "Captura salva no Poetrate."
                }
            } catch (error: Exception) {
                message.value = "Não foi possível salvar a captura. ${error.localizedMessage.orEmpty()}"
            } finally {
                clearPending()
                busy.value = false
            }
        }
    }

    private fun clearPending() {
        saved.get<String>("capture_name")?.let { name ->
            runCatching {
                val file = store.pendingFile(name)
                context.revokeUriPermission(store.uri(file), grants)
                file.delete()
            }
        }
        saved.remove<String>("capture_name")
        saved.remove<Boolean>("capture_video")
        saved.remove<String>("capture_album")
    }

    fun importSelected(uris: List<Uri>, album: String?) {
        if (uris.isEmpty() || busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                val copied = withContext(Dispatchers.IO) {
                    uris.count { uri -> runCatching { store.importUri(uri, album ?: "Importados") }.isSuccess }
                }
                message.value = if (copied == uris.size) "$copied mídia(s) copiada(s) para o Poetrate."
                    else "$copied de ${uris.size} copiadas. Alguns arquivos não puderam ser lidos ou não há espaço suficiente."
            } finally { busy.value = false }
        }
    }
}

@Composable
fun LibraryAddFab(album: String? = null, viewModel: LibraryAddViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var chooseCamera by rememberSaveable { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.completeCapture(it.resultCode, it.data?.data)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        viewModel.importSelected(it, album)
    }
    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.message.value = null
        }
    }
    AddMenuFab(
        actions = listOf(
            AddMenuAction("Abrir câmera", Icons.Outlined.PhotoCamera) { chooseCamera = true },
            AddMenuAction("Adicionar mídia", Icons.Outlined.AddPhotoAlternate) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
        ),
        busy = busy,
    )
    if (chooseCamera) {
        AlertDialog(
            onDismissRequest = { chooseCamera = false },
            title = { Text("Abrir câmera do celular") },
            text = { Text("Escolha o que deseja capturar com o aplicativo de câmera do seu celular.") },
            confirmButton = {
                TextButton(onClick = { chooseCamera = false; viewModel.prepareCapture(false, album) { camera.launch(it) } }) { Text("Foto") }
            },
            dismissButton = {
                TextButton(onClick = { chooseCamera = false; viewModel.prepareCapture(true, album) { camera.launch(it) } }) { Text("Vídeo") }
            },
        )
    }
}
