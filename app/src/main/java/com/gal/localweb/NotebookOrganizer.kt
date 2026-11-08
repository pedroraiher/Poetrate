package com.gal.localweb

import android.content.Context
import android.os.BatteryManager
import com.gal.model.Media
import com.gal.model.MediaType
import com.gal.organize.LocalOrganizer
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The notebook only controls local inference; originals never leave the phone for analysis. */
internal class NotebookOrganizer(private val context: Context, private val library: () -> List<Media>) {
    private val engine=LocalOrganizer(context)
    private val lock=Any()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var job:Job?=null
    @Volatile private var running=false
    @Volatile private var message="Pronto para analisar as fotos do celular."
    private val prefs=context.getSharedPreferences("organization",0)
    fun snapshot():JSONObject = synchronized(lock) {
        val active=library().filter{it.type==MediaType.IMAGE}.map{it.id}.toSet()
        val result=runBlocking{engine.snapshot(active)}
        JSONObject().put("running",running).put("status",message).put("charging",prefs.getBoolean("charging",false)).put("analyzed",result.analyzed)
            .put("people",JSONArray().apply{result.people.forEach{p->put(JSONObject().put("id",p.id).put("name",p.name).put("faces",JSONArray().apply{p.faces.forEach{f->put(JSONObject().put("key",f.key).put("mediaId",f.mediaId.toString()))}}))}})
            .put("similar",JSONArray().apply{result.similar.forEach{g->put(JSONArray(g.map{it.toString()}))}})
    }
    fun thumbnail(key:String):File = synchronized(lock) {
        require(Regex("\\d+_\\d+").matches(key))
        val id=key.substringBefore('_').toLong()
        require(library().any{it.id==id}) { "Foto não encontrada." }
        File(checkNotNull(engine.thumbnail(key).path)).also{require(it.isFile){"Prévia não encontrada."}}
    }
    fun action(action:String,data:JSONObject):JSONObject {
        if(action=="pause"){message="Pausado. Os resultados foram salvos.";job?.cancel();return JSONObject().put("ok",true)}
        synchronized(lock) {
            when(action){
                "charging"->prefs.edit().putBoolean("charging",data.getBoolean("value")).apply()
                "scan"->{
                    if(!running){
                        val photos=library().filter{it.type==MediaType.IMAGE}
                        running=true
                        job=scope.launch {
                            var failures=0
                            try {
                                if(photos.isNotEmpty())synchronized(lock){engine.prune(photos.map{it.id}.toSet());if(photos.any{!engine.cached(it)})engine.initialize()}
                                for((index,photo) in photos.withIndex()){
                                    ensureActive()
                                    if(prefs.getBoolean("charging",false)&&!(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).isCharging){message="Pausado: conecte o carregador e inicie novamente.";break}
                                    message="Analisando ${index+1} de ${photos.size} fotos…"
                                    try{synchronized(lock){if(!engine.cached(photo))engine.analyze(photo)}}catch(e:CancellationException){throw e}catch(e:Exception){failures++}
                                    if(index==photos.lastIndex)message="Análise concluída. $failures foto(s) não puderam ser analisadas."
                                }
                                if(photos.isEmpty())message="Nenhuma foto para analisar."
                            }catch(e:CancellationException){throw e}catch(e:Exception){message="Não foi possível analisar: ${e.message}"}finally{running=false}
                        }
                    }
                }
                "rename"->{require(!running){"Pause a análise antes de editar grupos."};engine.rename(data.getString("id"),data.getString("name"))}
                "merge"->{require(!running){"Pause a análise antes de unir grupos."};val ids=data.getJSONArray("ids");require(ids.length() in 1..10000);engine.merge((0 until ids.length()).map{ids.getString(it)}.toSet(),data.getString("into"))}
                "separate"->{require(!running){"Pause a análise antes de separar rostos."};engine.separate(data.getString("key"))}
                else->error("Ação não encontrada.")
            }
        }
        return JSONObject().put("ok",true)
    }
    fun close(){runBlocking{job?.cancelAndJoin()};scope.cancel();synchronized(lock){engine.close()}}
}
