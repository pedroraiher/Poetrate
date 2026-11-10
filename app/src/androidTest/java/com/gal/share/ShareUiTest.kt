package com.gal.share

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gal.MainActivity
import com.gal.data.local.PrivateMediaStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareUiTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun find(node:AccessibilityNodeInfo?,label:String):AccessibilityNodeInfo? {
        if(node==null)return null
        if(node.text?.toString()==label || node.contentDescription?.toString()==label)return node
        for(i in 0 until node.childCount)find(node.getChild(i),label)?.let{return it}
        return null
    }
    private fun waitFor(label:String):AccessibilityNodeInfo {
        repeat(100){find(instrumentation.uiAutomation.rootInActiveWindow,label)?.let{return it};Thread.sleep(100)}
        error("Missing UI: $label")
    }
    private fun click(label:String){
        repeat(30){
            var node=waitFor(label)
            while(!node.isClickable)node=node.parent ?: error("Not clickable: $label")
            if(node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){Thread.sleep(400);return}
            Thread.sleep(100)
        }
        error("Could not click: $label")
    }
    @Test fun shareDestinationAndAlbumActionsWorkThroughUI() {
        val context=instrumentation.targetContext;val store=PrivateMediaStore(context)
        val originalIds=store.list().map{it.id}.toSet()
        val name="UI album ${System.nanoTime()}";val album=store.createAlbum(name)
        val pending=store.newCapture(false);val bitmap=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
        pending.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
        val source=store.commit(pending,"image/jpeg","shared-ui.jpg","")
        val uri=store.list().single{it.id==source}.uri
        try {
            val intent=Intent(context,MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
            waitFor("Adicionar ao Poetrate");click("Adicionar")
            waitFor("1 arquivo(s) adicionado(s) à biblioteca.")
            assertEquals(2,store.list().count{it.id !in originalIds})
            click("Voltar à biblioteca")
            context.startActivity(Intent(context,MainActivity::class.java).setAction(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM,arrayListOf(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
            waitFor("Adicionar ao Poetrate");click(name);click("Adicionar")
            waitFor("1 arquivo(s) adicionado(s) à biblioteca e ao álbum.")
            val imported=store.list().single{it.albumId==album}.id
            click("Voltar à biblioteca");click("Albums")
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "Opções do álbum $name"))
            var card = waitFor(name)
            while (!card.isLongClickable) card = card.parent ?: error("Album has no long press")
            assertTrue(card.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
            click("Opções do álbum $name");click("Editar nome e capa")
            click("Escolher foto do álbum")
            click(store.list().single { it.id == imported }.displayName ?: "Foto do álbum")
            val input=waitFor(name)
            assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"$name edited")}))
            click("Salvar");click("Opções do álbum $name edited");click("Excluir álbum, manter fotos");click("Excluir somente o álbum")
            repeat(50){if(store.albums().none{it.id==album})return@repeat;Thread.sleep(100)}
            assertFalse(store.albums().any{it.id==album})
            assertEquals(-1L,store.list().single{it.id==imported}.albumId)
            assertTrue(store.file(imported)!!.exists());assertTrue(store.file(source)!!.exists())
        } finally {
            if(store.albums().any{it.id==album})store.deleteAlbum(album)
            store.list().filter{it.id !in originalIds}.forEach{store.delete(it.id)}
            store.close()
        }
    }
}
