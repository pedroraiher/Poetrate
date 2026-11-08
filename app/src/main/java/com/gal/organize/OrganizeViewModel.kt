package com.gal.organize

import android.content.Context
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gal.data.repository.MediaRepository
import com.gal.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class OrganizeViewModel @Inject constructor(@ApplicationContext private val context: Context, repository: MediaRepository) : ViewModel() {
    private val engine=LocalOrganizer(context)
    private val lock=Mutex()
    private val prefs=context.getSharedPreferences("organization",0)
    val library=repository.timeline().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val results=MutableStateFlow(Organization())
    val running=MutableStateFlow(false)
    val status=MutableStateFlow("Toque em Analisar fotos. Tudo acontece neste celular.")
    val chargingOnly=MutableStateFlow(prefs.getBoolean("charging",false))
    private var job: Job?=null
    init { viewModelScope.launch { library.collect { refresh() } } }
    private fun active()=library.value.filter { it.type==MediaType.IMAGE && !it.isTrashed }
    private suspend fun refresh()=withContext(Dispatchers.IO) { lock.withLock { results.value=engine.snapshot(active().map { it.id }.toSet()) } }
    fun reload(){viewModelScope.launch{refresh()}}
    fun thumbnail(key:String)=engine.thumbnail(key)
    fun charging(value:Boolean){chargingOnly.value=value;prefs.edit().putBoolean("charging",value).apply()}
    fun pause(){if(running.value){status.value="Pausado. Os resultados já analisados foram salvos.";job?.cancel()}}
    fun scan() {
        if(running.value)return
        running.value=true
        job=viewModelScope.launch(Dispatchers.IO) {
            var failures=0
            try {
                val photos=active()
                if(photos.isNotEmpty())lock.withLock {
                    engine.prune(photos.map { it.id }.toSet())
                    if(photos.any { !engine.cached(it) })engine.initialize()
                }
                for((index,photo) in photos.withIndex()) {
                    ensureActive()
                    if(chargingOnly.value && !(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).isCharging){status.value="Pausado: conecte o carregador e toque em Continuar análise.";break}
                    status.value="Analisando ${index+1} de ${photos.size} fotos…"
                    try { lock.withLock { if(!engine.cached(photo))engine.analyze(photo) } }
                    catch(e:CancellationException){throw e}
                    catch(e:Exception){ failures++;status.value="Não foi possível analisar ${photo.displayName ?: "uma foto"}." }
                    if(index%10==0)refresh()
                    if(index==photos.lastIndex)status.value="Análise concluída. $failures foto(s) não puderam ser analisadas."
                }
                if(photos.isEmpty())status.value="Não há fotos para analisar. Vídeos não são analisados nesta versão."
            } catch(e:CancellationException){throw e}
            catch(e:Exception){status.value="Não foi possível concluir: ${e.message}"}
            finally { withContext(NonCancellable+Dispatchers.IO){try{refresh()}finally{running.value=false}} }
        }
    }
    private fun edit(action:()->Unit){if(running.value)return;viewModelScope.launch(Dispatchers.IO){try{lock.withLock{action()};refresh()}catch(e:Exception){status.value="Não foi possível salvar: ${e.message}"}}}
    fun rename(id:String,name:String)=edit{engine.rename(id,name)}
    fun merge(from:Set<String>,into:String)=edit{engine.merge(from,into)}
    fun separate(key:String)=edit{engine.separate(key)}
    override fun onCleared(){job?.cancel();super.onCleared()}
}
