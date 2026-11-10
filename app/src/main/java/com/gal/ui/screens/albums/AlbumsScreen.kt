package com.gal.ui.screens.albums

import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.gal.data.repository.MediaRepository
import com.gal.model.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import android.net.Uri
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.CreateNewFolder
import com.gal.data.local.PrivateMediaStore
import com.gal.ui.components.AddMenuFab
import com.gal.ui.components.AddMenuAction
import com.gal.ui.components.PrivateThumbnail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val store: PrivateMediaStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val albums = repository.albums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Album>())
    val creating = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    fun create(name: String, cover: Uri?, onCreated: () -> Unit) {
        if (creating.value) return
        viewModelScope.launch {
            creating.value = true
            error.value = null
            try {
                withContext(Dispatchers.IO) { store.createAlbum(name, cover) }
                cover?.let { runCatching { context.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                onCreated()
            } catch (failure: Exception) {
                error.value = failure.localizedMessage ?: "Não foi possível criar o álbum."
            } finally { creating.value = false }
        }
    }

    fun coverImages(albumId: Long) = repository.album(albumId)

    fun edit(album: Album, name: String, cover: Uri?, reset: Boolean, done: () -> Unit) = change(done) { store.editAlbum(album.id,name,cover,reset) }
    fun delete(album: Album, done: () -> Unit) = change(done) { store.deleteAlbum(album.id) }
    private fun change(done: () -> Unit, action: () -> Unit) {
        if(creating.value)return
        creating.value=true;error.value=null
        viewModelScope.launch {
            try {withContext(Dispatchers.IO){action()};done()}
            catch(e:Exception){error.value=e.message ?: "Não foi possível alterar o álbum."}
            finally{creating.value=false}
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onAlbumClick: (Album) -> Unit,
    onTrashClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    val createError by viewModel.error.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    if (showCreate) CreateAlbumDialog(
        busy = creating, error = createError,
        onDismiss = { showCreate = false; viewModel.error.value = null },
        onCreate = { name, cover -> viewModel.create(name, cover) { showCreate = false } },
    )
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = albums.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    var columns by remember { mutableIntStateOf(2) }
    var pinchAccum by remember { mutableFloatStateOf(1f) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = {
            AddMenuFab(listOf(AddMenuAction("Novo álbum", Icons.Outlined.CreateNewFolder) {
                viewModel.error.value = null
                showCreate = true
            }), busy = creating)
        },
        topBar = {
            TopAppBar(
                title = { Text(selected?.name ?: "Álbuns") },
                navigationIcon = { if (selected != null) androidx.compose.material3.TextButton(onClick = { selectedId = null }) { Text("Cancelar") } },
                actions = {
                    if (selected != null) {
                        AlbumActions(selected, onDeleted = { selectedId = null }, vm = viewModel)
                    } else {
                    IconButton(onClick = { columns = if (columns >= 4) 1 else columns + 1 }) {
                        Icon(
                            if (columns <= 2) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                            contentDescription = "Change grid size",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Trash") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = { showMenu = false; onTrashClick() },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                onClick = { showMenu = false; onSettingsClick() },
                            )
                        }
                    }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (albums.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nenhum álbum ainda.\nToque em + para criar o primeiro.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp +
                        com.gal.ui.navigation.LocalFloatingNavBarPadding.current + 88.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            pinchAccum *= zoom
                            when {
                                pinchAccum > 1.3f -> {
                                    columns = (columns - 1).coerceIn(1, 4)
                                    pinchAccum = 1f
                                }
                                pinchAccum < 0.75f -> {
                                    columns = (columns + 1).coerceIn(1, 4)
                                    pinchAccum = 1f
                                }
                            }
                        }
                    },
            ) {
                items(albums, key = { it.id }) { album ->
                    AlbumCard(
                        album = album,
                        compact = columns >= 3,
                        onClick = { if (selected != null) selectedId = album.id else onAlbumClick(album) },
                        onLongClick = { selectedId = album.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(if (compact) 8.dp else 16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Opções do álbum"),
    ) {
        Box {
        PrivateThumbnail(
            uri = album.coverUri,
            isVideo = album.coverIsVideo,
            description = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape),
        )
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = if (compact) 4.dp else 8.dp)) {
            Text(
                text = album.name,
                style = if (compact) MaterialTheme.typography.bodyMedium
                        else MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${album.mediaCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
