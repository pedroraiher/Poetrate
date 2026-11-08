package com.gal.organize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gal.model.Media
import com.gal.ui.components.FastScrollList
import com.gal.ui.components.PrivateThumbnail
import com.gal.ui.navigation.LocalFloatingNavBarPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(onMediaClick:(Media)->Unit, vm:OrganizeViewModel=hiltViewModel()) {
    val results by vm.results.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val charging by vm.chargingOnly.collectAsStateWithLifecycle()
    val media=remember(library){library.associateBy{it.id}}
    var tab by remember{mutableIntStateOf(0)}
    var personId by remember{mutableStateOf<String?>(null)}
    var rename by remember{mutableStateOf<PersonGroup?>(null)}
    var name by remember{mutableStateOf("")}
    var merge by remember{mutableStateOf<PersonGroup?>(null)}
    var mergeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var separate by remember{mutableStateOf<FaceEntry?>(null)}
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)vm.pause() else if(event==Lifecycle.Event.ON_START)vm.reload()};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer);vm.pause()}}
    BackHandler(personId!=null){personId=null}
    Scaffold(topBar={TopAppBar(title={Text("Organizar")})}) { padding ->
        FastScrollList(Modifier.fillMaxSize().padding(padding).padding(bottom=LocalFloatingNavBarPadding.current),contentPadding=PaddingValues(start=16.dp,end=8.dp,bottom=24.dp)) {
            item { Text("Análise local · suas fotos permanecem neste celular.",style=MaterialTheme.typography.bodyMedium) }
            item { Text(status); Row { Button(onClick={if(running)vm.pause() else vm.scan()}){Text(if(running)"Pausar" else if(results.analyzed>0)"Continuar análise" else "Analisar fotos")}; if(running)CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp)) } }
            item { Row { Switch(checked=charging,onCheckedChange=vm::charging);Text("Analisar somente carregando",Modifier.padding(12.dp)) };Text("A análise pausa ao sair desta tela. Ao continuar, só processa fotos novas ou alteradas. Vídeos não são analisados.",style=MaterialTheme.typography.bodySmall) }
            item { Row { FilterChip(selected=tab==0,onClick={tab=0;personId=null},label={Text("Pessoas (${results.people.size})")});Spacer(Modifier.width(8.dp));FilterChip(selected=tab==1,onClick={tab=1;personId=null},label={Text("Fotos semelhantes")}) } }
            if(tab==0) {
                val person=results.people.firstOrNull{it.id==personId}
                if(person==null) {
                    item { Text("Toque em um rosto para ver as fotos, dar um nome ou corrigir o grupo. Agrupamentos são sugestões.") }
                    if(results.people.isEmpty())item{Text(if(running)"Os rostos encontrados aparecerão aqui." else "Nenhum rosto analisado ainda. Inicie a análise acima.")}
                    items(results.people,key={it.id}){group->
                        Card(Modifier.fillMaxWidth().clickable{personId=group.id}){Row(Modifier.padding(12.dp)){
                            PrivateThumbnail(vm.thumbnail(group.faces.first().key),false,group.name,Modifier.size(72.dp))
                            Column(Modifier.padding(start=12.dp)){Text(group.name,style=MaterialTheme.typography.titleMedium);Text("${group.faces.map{it.mediaId}.distinct().size} fotos");Text("Abrir grupo")}
                        }}
                    }
                } else {
                    item { TextButton(onClick={personId=null}){Text("← Todas as pessoas")};Text(person.name,style=MaterialTheme.typography.headlineSmall);Row{TextButton(enabled=!running,onClick={rename=person;name=person.name}){Text("Dar nome")};TextButton(enabled=!running&&results.people.size>1,onClick={mergeIds=emptySet();merge=person}){Text("Unir grupos")}} }
                    items(person.faces,key={it.key}){face->media[face.mediaId]?.let{photo->
                        Card{Column(Modifier.padding(12.dp)){Row{
                            PrivateThumbnail(vm.thumbnail(face.key),false,"Rosto",Modifier.size(64.dp))
                            PrivateThumbnail(photo.uri,false,photo.displayName,Modifier.padding(start=8.dp).size(140.dp).clickable{vm.pause();onMediaClick(photo)})
                        };Text(photo.displayName ?: "Foto");TextButton(enabled=!running,onClick={separate=face}){Text("Não é esta pessoa · separar")}}}
                    }}
                }
            } else {
                item { Text("Sugestões por aparência e composição. Nada é apagado automaticamente. Toque na foto para abrir e usar as ações da galeria.") }
                if(results.similar.isEmpty())item{Text(if(running)"Procurando fotos parecidas…" else "Nenhum conjunto semelhante encontrado na análise atual.")}
                items(results.similar){ids->Card{Column(Modifier.padding(12.dp)){Text("${ids.size} fotos semelhantes");FastScrollList(horizontal=true){items(ids,key={it}){id->media[id]?.let{photo->PrivateThumbnail(photo.uri,false,photo.displayName,Modifier.size(140.dp).clickable{vm.pause();onMediaClick(photo)})}}}}}}
            }
        }
    }
    rename?.let{group->AlertDialog(onDismissRequest={rename=null},title={Text("Nome da pessoa")},text={OutlinedTextField(name,{name=it},singleLine=true)},confirmButton={TextButton(onClick={vm.rename(group.id,name);rename=null}){Text("Salvar")}},dismissButton={TextButton(onClick={rename=null}){Text("Cancelar")}})}
    merge?.let { group ->
        val available=results.people.filter{it.id!=group.id}
        val chosen=mergeIds.intersect(available.map{it.id}.toSet())
        AlertDialog(
            onDismissRequest={merge=null}, title={Text("Unir vários grupos")},
            text={Column {
                Text("Marque os grupos da mesma pessoa. Eles serão unidos a ${group.name}, mantendo esse nome.")
                TextButton(enabled=!running,onClick={mergeIds=if(chosen.size==available.size)emptySet() else available.map{it.id}.toSet()}) { Text(if(chosen.size==available.size)"Desmarcar todos" else "Selecionar todos") }
                FastScrollList(Modifier.fillMaxWidth().heightIn(min=120.dp,max=380.dp)) {
                    items(available,key={it.id}) { target ->
                        Row(Modifier.fillMaxWidth().clickable(enabled=!running){mergeIds=if(target.id in mergeIds)mergeIds-target.id else mergeIds+target.id}.padding(vertical=8.dp)) {
                            Checkbox(checked=target.id in chosen,onCheckedChange=null)
                            PrivateThumbnail(vm.thumbnail(target.faces.first().key),false,target.name,Modifier.size(48.dp))
                            Column(Modifier.weight(1f).padding(start=8.dp)){Text(target.name);Text("${target.faces.map{it.mediaId}.distinct().size} fotos",style=MaterialTheme.typography.bodySmall)}
                        }
                    }
                }
            }},
            confirmButton={TextButton(enabled=chosen.isNotEmpty()&&!running,onClick={vm.merge(chosen,group.id);personId=group.id;merge=null}){Text("Unir ${chosen.size} selecionados")}},
            dismissButton={TextButton(onClick={merge=null}){Text("Cancelar")}},
        )
    }

    separate?.let{face->AlertDialog(onDismissRequest={separate=null},title={Text("Separar este rosto?")},text={Text("Ele irá para um novo grupo. Você poderá dar um nome ou unir ao grupo correto. A foto original permanece intacta.")},confirmButton={TextButton(onClick={vm.separate(face.key);separate=null}){Text("Separar")}},dismissButton={TextButton(onClick={separate=null}){Text("Cancelar")}})}
}
