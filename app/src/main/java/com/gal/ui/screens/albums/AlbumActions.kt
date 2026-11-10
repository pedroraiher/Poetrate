package com.gal.ui.screens.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.gal.model.MediaType
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gal.model.Album
import com.gal.ui.components.PrivateThumbnail

@Composable
fun AlbumActions(album:Album,onDeleted:()->Unit={},vm:AlbumsViewModel=hiltViewModel()) {
    var menu by remember{mutableStateOf(false)}
    var edit by rememberSaveable(album.id){mutableStateOf(false)}
    var delete by rememberSaveable(album.id){mutableStateOf(false)}
    val busy by vm.creating.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    Box {
        IconButton(enabled=!busy,onClick={menu=true}){Icon(Icons.Outlined.MoreVert,contentDescription="Opções do álbum ${album.name}")}
        DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
            DropdownMenuItem(text={Text("Editar nome e capa")},onClick={menu=false;vm.error.value=null;edit=true})
            DropdownMenuItem(text={Text("Excluir álbum, manter fotos")},onClick={menu=false;vm.error.value=null;delete=true})
        }
    }
    if(edit)EditAlbumDialog(album,busy,error,vm,onDismiss={edit=false},onSave={name,cover,reset->vm.edit(album,name,cover,reset){edit=false}})
    if(delete)AlertDialog(onDismissRequest={if(!busy)delete=false},title={Text("Excluir ${album.name}?")},text={Column{Text("Somente o álbum será excluído. Todas as fotos e vídeos continuam na biblioteca geral, com suas posições e favoritos preservados.");error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(enabled=!busy,onClick={vm.delete(album){delete=false;onDeleted()}}){Text(if(busy)"Excluindo…" else "Excluir somente o álbum")}},dismissButton={TextButton(enabled=!busy,onClick={delete=false}){Text("Cancelar")}})
}

@Composable
private fun EditAlbumDialog(album:Album,busy:Boolean,error:String?,vm:AlbumsViewModel,onDismiss:()->Unit,onSave:(String,Uri?,Boolean)->Unit){
    var name by rememberSaveable(album.id){mutableStateOf(album.name)}
    var cover by rememberSaveable(album.id){mutableStateOf<String?>(null)}
    var reset by rememberSaveable(album.id){mutableStateOf(false)}
    var choosing by rememberSaveable { mutableStateOf(false) }
    val mediaFlow = remember(album.id) { vm.coverImages(album.id) }
    val media by mediaFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val images = media.filter { it.type == MediaType.IMAGE }
    if (choosing) AlertDialog(
        onDismissRequest = { choosing = false },
        title = { Text("Escolher foto do álbum") },
        text = {
            if (images.isEmpty()) Text("Este álbum ainda não tem fotos. Adicione uma foto para escolher a capa.")
            else LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth().height(360.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(images, key = { it.id }) { image ->
                    PrivateThumbnail(image.uri, false, image.displayName ?: "Foto do álbum", Modifier.aspectRatio(1f).clickable { cover = image.uri.toString(); reset = false; choosing = false })
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text("Cancelar") } },
    )
    if (!choosing) AlertDialog(onDismissRequest={if(!busy){onDismiss()}},title={Text("Editar álbum")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        OutlinedTextField(name,{name=it.take(100)},label={Text("Nome do álbum")},enabled=!busy,singleLine=true)
        val preview=cover?.let(Uri::parse) ?: if(reset)null else album.coverUri
        PrivateThumbnail(preview,cover==null&&!reset&&album.coverIsVideo,"Capa do álbum",Modifier.fillMaxWidth().height(150.dp))
        TextButton(enabled=!busy,onClick={choosing=true}){Text("Escolher foto do álbum")}
        TextButton(enabled=!busy,onClick={cover=null;reset=true}){Text("Usar capa automática")}
        Text("A capa automática usa uma foto ou vídeo do álbum. Alterar a capa não altera seus arquivos.",style=MaterialTheme.typography.bodySmall)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(enabled=!busy&&name.isNotBlank(),onClick={onSave(name,cover?.let(Uri::parse),reset)}){Text(if(busy)"Salvando…" else "Salvar")}},dismissButton={TextButton(enabled=!busy,onClick={onDismiss()}){Text("Cancelar")}})
}
