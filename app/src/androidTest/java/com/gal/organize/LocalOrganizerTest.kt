package com.gal.organize

import android.content.Context
import android.content.ContextWrapper
import android.graphics.*
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gal.model.Media
import com.gal.model.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Date

@RunWith(AndroidJUnit4::class)
class LocalOrganizerTest {
    @Test fun offlineInferenceCacheSimilarityAndManualCorrections()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val base=instrumentation.targetContext
        val dir=File(base.cacheDir,"organization_test_${System.nanoTime()}").apply{mkdirs()}
        val dbName="organization_test_${System.nanoTime()}.db"
        val context=object:ContextWrapper(base){
            override fun getFilesDir()=dir
            override fun getDatabasePath(name:String)=base.getDatabasePath(dbName)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?)=base.openOrCreateDatabase(dbName,mode,factory)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?,handler:android.database.DatabaseErrorHandler?)=base.openOrCreateDatabase(dbName,mode,factory,handler)
        }
        fun media(id:Long,bitmap:Bitmap):Media {
            val file=File(dir,"$id.jpg");file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)}
            return Media(id,Uri.fromFile(file),MediaType.IMAGE,"image/jpeg","$id.jpg",0,"test",Date(),Date(file.lastModified()),bitmap.width,bitmap.height,0,file.length())
        }
        val original=instrumentation.context.assets.open("face_fixture.jpg").use{BitmapFactory.decodeStream(it)}
        val scaled=Bitmap.createScaledBitmap(original,400,400,true)
        val blank=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.BLUE)}
        val images=listOf(media(1,original),media(2,scaled),media(3,blank))
        original.recycle();scaled.recycle();blank.recycle()
        var engine=LocalOrganizer(context)
        try {
            images.forEach{engine.analyze(it);assertTrue(engine.cached(it))}
            val result=engine.snapshot(setOf(1,2,3))
            assertEquals(3,result.analyzed)
            assertEquals("Same face must be grouped across resize",1,result.people.size)
            assertEquals(setOf(1L,2L),result.people.single().faces.map{it.mediaId}.toSet())
            assertTrue("Resized image should be suggested as similar",result.similar.any{it.toSet()==setOf(1L,2L)})
            assertTrue(result.similar.none{3L in it})
            val person=result.people.single()
            engine.rename(person.id,"Teste local")
            engine.separate(person.faces.last().key)
            assertEquals(2,engine.snapshot(setOf(1,2,3)).people.size)
            engine.close();engine=LocalOrganizer(context)
            val restored=engine.snapshot(setOf(1,2,3))
            assertEquals(2,restored.people.size)
            assertTrue(restored.people.any{it.name=="Teste local"})
            engine.analyze(images[0].copy(id=4))
            engine.separate(engine.snapshot(setOf(1,2,3,4)).people.flatMap{it.faces}.first{it.mediaId==4L}.key)
            val three=engine.snapshot(setOf(1,2,3,4))
            assertEquals(3,three.people.size)
            try { engine.merge(setOf(three.people.first{it.id!=person.id}.id,"missing"),person.id);fail("Missing group should reject batch") } catch(expected:IllegalArgumentException) { }
            assertEquals(3,engine.snapshot(setOf(1,2,3,4)).people.size)
            val remote=com.gal.localweb.NotebookOrganizer(context){images+images[0].copy(id=4)}
            try {
                remote.action("merge",org.json.JSONObject().put("ids",org.json.JSONArray((three.people.map{it.id}.toSet()-person.id).toList())).put("into",person.id))
                remote.action("rename",org.json.JSONObject().put("id",person.id).put("name","Nome pelo notebook"))
                assertEquals("Nome pelo notebook",remote.snapshot().getJSONArray("people").getJSONObject(0).getString("name"))
                assertTrue(remote.thumbnail(person.faces.first().key).isFile)
                try {remote.thumbnail("../private_media.db");fail("Invalid thumbnail path accepted")} catch(expected:IllegalArgumentException) {}
            } finally {remote.close()}
            assertEquals(4,engine.snapshot(setOf(1,2,3,4)).analyzed)
            assertEquals(3,engine.snapshot(setOf(1,2,3,4)).people.single().faces.size)
            assertEquals(1,engine.snapshot(setOf(1,2,3)).people.size)
            engine.prune(setOf(1,3))
            assertEquals(listOf(1L),engine.snapshot(setOf(1,3)).people.single().faces.map{it.mediaId})
            assertFalse(engine.cached(images[0].copy(sizeBytes=images[0].sizeBytes+1)))
        } finally {engine.close();base.deleteDatabase(dbName);dir.deleteRecursively()}
    }
}
