package com.gal.ui.screens.share

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gal.data.local.PrivateMediaStore
import com.gal.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/** Accept URI streams only; never interpret a shared filename or text as a local path. */
fun sharedUris(intent: Intent): List<Uri> {
    if(intent.action !in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE))return emptyList()
    val streams=if(intent.action==Intent.ACTION_SEND_MULTIPLE)intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM,Uri::class.java).orEmpty()
        else listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri::class.java))
    val candidates=if(streams.isNotEmpty())streams else intent.clipData?.let { data -> (0 until data.itemCount).mapNotNull{data.getItemAt(it).uri} }.orEmpty()
    require(candidates.isNotEmpty() && candidates.all{it.scheme=="content"}) { "O compartilhamento não contém fotos ou vídeos acessíveis. Compartilhe os arquivos pelo app de origem." }
    return candidates.distinct()
}

@HiltViewModel
class ReceiveShareViewModel @Inject constructor(private val store:PrivateMediaStore,repository:MediaRepository,private val saved:SavedStateHandle):ViewModel(){
    val active=saved.getStateFlow("share_active",false)
    val pending=saved.getStateFlow("share_pending",arrayListOf<String>())
    val albums=repository.albums().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val busy=MutableStateFlow(false)
    val message=MutableStateFlow<String?>(null)
    fun accept(intent:Intent){
        if(intent.action !in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE))return
        saved["share_active"]=true
        try{val incoming=sharedUris(intent).map{it.toString()};saved["share_pending"]=ArrayList((pending.value+incoming).distinct());message.value=null}
        catch(e:Exception){message.value=e.message ?: "Não foi possível ler o compartilhamento."}
    }
    fun dismiss(){if(!busy.value){saved["share_active"]=false;saved["share_pending"]=arrayListOf<String>();message.value=null}}
    fun create(name:String,onCreated:(Long)->Unit){if(busy.value)return;viewModelScope.launch{busy.value=true;try{val id=withContext(Dispatchers.IO){store.createAlbum(name)};onCreated(id)}catch(e:Exception){message.value=e.message}finally{busy.value=false}}}
    fun save(albumId:Long?){
        if(busy.value||pending.value.isEmpty())return
        val batch=pending.value.toList();busy.value=true
        viewModelScope.launch{
            var copied=0;var failed=0;var reason=""
            try {
                for((index,uri) in batch.withIndex()){
                    message.value="Adicionando ${index+1} de ${batch.size}…"
                    try{
                        withContext(Dispatchers.IO){store.importShared(Uri.parse(uri),albumId)}
                        copied++;saved["share_pending"]=ArrayList(pending.value.filterNot{it==uri})
                    }catch(e:CancellationException){throw e}catch(e:Exception){failed++;reason=e.message.orEmpty()}
                }
                message.value=if(failed==0) "$copied arquivo(s) adicionado(s) à biblioteca${if(albumId!=null)" e ao álbum" else ""}." else "$copied adicionado(s); $failed não puderam ser importados. $reason Os arquivos que falharam continuam selecionados para tentar novamente."
            }finally{busy.value=false}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveShareScreen(vm:ReceiveShareViewModel){
    val pending by vm.pending.collectAsStateWithLifecycle();val albums by vm.albums.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle();val message by vm.message.collectAsStateWithLifecycle()
    var destination by rememberSaveable{mutableStateOf<Long?>(null)}
    var newAlbum by rememberSaveable{mutableStateOf(false)};var name by rememberSaveable{mutableStateOf("")}
    BackHandler {if(!busy)vm.dismiss()}
    Scaffold(topBar={TopAppBar(title={Text("Adicionar ao Poetrate")})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("${pending.size} arquivo(s) para adicionar",style=MaterialTheme.typography.titleMedium)
            Text("Escolha o destino. Todas as fotos e vídeos adicionados a um álbum também aparecem na página inicial. Os originais no app de origem não serão alterados.")
            LazyColumn(Modifier.weight(1f)){
                item { Row(Modifier.fillMaxWidth().clickable(enabled=!busy){destination=null}.padding(8.dp)){RadioButton(selected=destination==null,onClick={if(!busy)destination=null},enabled=!busy);Column{Text("Biblioteca geral");Text("Somente na página inicial, sem álbum.",style=MaterialTheme.typography.bodySmall)}} }
                items(albums,key={it.id}){album->Row(Modifier.fillMaxWidth().clickable(enabled=!busy){destination=album.id}.padding(8.dp)){RadioButton(selected=destination==album.id,onClick={if(!busy)destination=album.id},enabled=!busy);Column{Text(album.name);Text("${album.mediaCount} itens",style=MaterialTheme.typography.bodySmall)}}}
                item {TextButton(enabled=!busy,onClick={name="";newAlbum=true}){Text("Criar novo álbum")}}
            }
            message?.let{Text(it)}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Button(enabled=!busy&&pending.isNotEmpty()&&(destination==null||albums.any{it.id==destination}),onClick={vm.save(destination)}){Text("Adicionar")}
                TextButton(enabled=!busy,onClick=vm::dismiss){Text(if(pending.isEmpty())"Voltar à biblioteca" else "Cancelar")}
            }
        }
    }
    if(newAlbum)AlertDialog(onDismissRequest={if(!busy)newAlbum=false},title={Text("Novo álbum")},text={OutlinedTextField(name,{name=it.take(100)},label={Text("Nome")},enabled=!busy,singleLine=true)},confirmButton={TextButton(enabled=!busy&&name.isNotBlank(),onClick={vm.create(name){destination=it;newAlbum=false}}){Text("Criar")}},dismissButton={TextButton(enabled=!busy,onClick={newAlbum=false}){Text("Cancelar")}})
}
