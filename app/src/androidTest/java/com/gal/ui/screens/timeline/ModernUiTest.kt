package com.gal.ui.screens.timeline

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gal.MainActivity
import com.gal.data.local.PrivateMediaStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModernUiTest {
    @Test fun orientationAndGlassRender() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val ctx = inst.targetContext
        val store = PrivateMediaStore(ctx)
        val ids = mutableListOf<Long>()
        try {
            repeat(30) { i ->
                val bmp = Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.HSVToColor(floatArrayOf(i*12f,.65f,.85f)))
                val paint=android.graphics.Paint().apply { color=Color.WHITE;strokeWidth=8f }
                for(x in 0..240 step 40)canvas.drawLine(x.toFloat(),0f,x.toFloat(),240f,paint)
                val f=store.newCapture(false)
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) };bmp.recycle()
                ids += store.commit(f,"image/png","Glass test $i", "UI verification")
            }
            val square=store.list().first { it.id in ids }
            assertTrue(matchesOrientation(square,OrientationFilter.HORIZONTAL))
            assertTrue(matchesOrientation(square,OrientationFilter.VERTICAL))
            val wide=square.copy(width=1920,height=1080,orientation=0)
            assertTrue(matchesOrientation(wide,OrientationFilter.HORIZONTAL))
            assertFalse(matchesOrientation(wide,OrientationFilter.VERTICAL))
            assertTrue(matchesOrientation(wide.copy(orientation=90),OrientationFilter.VERTICAL))
            assertFalse(matchesOrientation(wide.copy(width=0),OrientationFilter.HORIZONTAL))
            ctx.startActivity(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Thread.sleep(3500)
            val shot=inst.uiAutomation.takeScreenshot()
            assertNotNull(shot)
            java.io.File(ctx.cacheDir,"modern-ui.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG,100,it) }
            shot.recycle()
            assertEquals(ctx.packageName,inst.uiAutomation.rootInActiveWindow.packageName.toString())
        } finally { ids.forEach { store.delete(it) };store.close() }
    }
}
