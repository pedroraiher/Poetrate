package com.gal.organize

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.gal.model.Media
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import org.opencv.objdetect.FaceRecognizerSF
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.*

data class FaceEntry(val key: String, val mediaId: Long, val person: String, val vector: FloatArray)
data class PersonGroup(val id: String, val name: String, val faces: List<FaceEntry>)
data class ImageSignature(val id: Long, val hash: Long, val colors: FloatArray)
data class Organization(val people: List<PersonGroup> = emptyList(), val similar: List<List<Long>> = emptyList(), val analyzed: Int = 0)

/** All descriptors, face crops and corrections stay in the private application directory. */
class LocalOrganizer(private val context: Context) : AutoCloseable {
    private val helper = object : SQLiteOpenHelper(context, "organization.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE images(id INTEGER PRIMARY KEY, revision TEXT NOT NULL, hash INTEGER NOT NULL, colors BLOB NOT NULL)")
            db.execSQL("CREATE TABLE people(id TEXT PRIMARY KEY, name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE faces(key TEXT PRIMARY KEY, media INTEGER NOT NULL, person TEXT NOT NULL, vector BLOB NOT NULL)")
            db.execSQL("CREATE INDEX faces_person ON faces(person)")
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    }
    private val db get() = helper.writableDatabase
    private var detector: FaceDetectorYN? = null
    private var recognizer: FaceRecognizerSF? = null
    private val directory = File(context.filesDir, "organization_faces").apply { mkdirs() }
    fun thumbnail(key: String): Uri = Uri.fromFile(File(directory, "$key.jpg"))
    private fun pack(v: FloatArray) = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { v.forEach { putFloat(it) } }.array()
    private fun unpack(b: ByteArray): FloatArray = FloatArray(b.size / 4).also { ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }
    private fun revision(m: Media) = "1:${m.dateModified.time}:${m.sizeBytes}:${m.uri}"
    fun cached(m: Media): Boolean = db.rawQuery("SELECT revision FROM images WHERE id=?", arrayOf(m.id.toString())).use { it.moveToFirst() && it.getString(0) == revision(m) }
    private fun model(name: String): String {
        val target = File(context.filesDir, "organization_models/$name")
        target.parentFile!!.mkdirs()
        val length = context.assets.openFd("ai/$name").use { it.length }
        if (!target.isFile || target.length() != length) {
            val pending = File(target.parentFile, "$name.tmp")
            context.assets.open("ai/$name").use { input -> pending.outputStream().use { input.copyTo(it) } }
            check(pending.renameTo(target))
        }
        return target.absolutePath
    }
    fun initialize() {
        if (detector != null) return
        check(OpenCVLoader.initLocal()) { "Não foi possível iniciar a análise local." }
        Core.setNumThreads(2)
        detector = FaceDetectorYN.create(model("yunet.onnx"), "", Size(640.0,640.0), .85f, .3f, 5000)
        recognizer = FaceRecognizerSF.create(model("sface.onnx"), "")
    }
    private fun faces(): List<FaceEntry> = db.rawQuery("SELECT key,media,person,vector FROM faces ORDER BY key", null).use { c -> buildList { while(c.moveToNext()) add(FaceEntry(c.getString(0),c.getLong(1),c.getString(2),unpack(c.getBlob(3)))) } }
    private fun person(): String = UUID.randomUUID().toString().also { db.execSQL("INSERT INTO people VALUES(?,?)", arrayOf(it, "Pessoa")) }
    private fun cosine(a: FloatArray, b: FloatArray): Float { var dot=0f; var aa=0f; var bb=0f; for(i in a.indices){ dot+=a[i]*b[i]; aa+=a[i]*a[i]; bb+=b[i]*b[i] }; return dot / sqrt(aa*bb).coerceAtLeast(.000001f) }

    /** Each completed photo is committed independently, so pause/restart never loses the index. */
    fun analyze(media: Media) {
        initialize()
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, media.uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = min(1.0, 960.0 / max(info.size.width,info.size.height))
            decoder.setTargetSize(max(1,(info.size.width*scale).toInt()),max(1,(info.size.height*scale).toInt()))
        }
        val rgba=Mat(); val bgr=Mat(); val detected=Mat()
        val crops=mutableListOf<Pair<String,Bitmap>>()
        try {
            val signature = signature(bitmap, media.id)
            Utils.bitmapToMat(bitmap,rgba); Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR)
            detector!!.setInputSize(bgr.size()); detector!!.detect(bgr,detected)
            val previous=faces().filter { it.mediaId != media.id }
            val candidates=previous.groupBy { it.person }.mapValues { (_,f) -> f.take(5) }
            val used=mutableSetOf<String>()
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM faces WHERE media=?", arrayOf(media.id))
                for(i in 0 until detected.rows()) {
                    val row=detected.row(i); val aligned=Mat(); val feature=Mat(); val rgb=Mat()
                    try {
                        if (row.get(0,2)[0] < 32 || row.get(0,3)[0] < 32) continue
                        recognizer!!.alignCrop(bgr,row,aligned); recognizer!!.feature(aligned,feature)
                        val vector=FloatArray(feature.total().toInt()); feature.get(0,0,vector)
                        val ranked=candidates.filterKeys { it !in used }.map { (id,list) -> id to list.maxOf { cosine(vector,it.vector) } }.sortedByDescending { it.second }
                        val best=ranked.firstOrNull()
                        // A margin avoids silently picking one of two very similar people.
                        val group=if(best!=null && best.second >= .55f && (ranked.size==1 || best.second-ranked[1].second>=.06f)) best.first else person()
                        used.add(group)
                        val key="${media.id}_$i"
                        db.execSQL("INSERT INTO faces VALUES(?,?,?,?)", arrayOf(key,media.id,group,pack(vector)))
                        Imgproc.cvtColor(aligned,rgb,Imgproc.COLOR_BGR2RGBA)
                        val crop=Bitmap.createBitmap(rgb.cols(),rgb.rows(),Bitmap.Config.ARGB_8888); Utils.matToBitmap(rgb,crop); crops.add(key to crop)
                    } finally { row.release(); aligned.release(); feature.release(); rgb.release() }
                }
                db.execSQL("INSERT OR REPLACE INTO images VALUES(?,?,?,?)", arrayOf(media.id,revision(media),signature.hash,pack(signature.colors)))
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            crops.forEach { (key,crop) -> File(directory,"$key.jpg").outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG,85,it) } }
        } finally { crops.forEach { it.second.recycle() }; bitmap.recycle(); rgba.release(); bgr.release(); detected.release() }
    }
    fun prune(active: Set<Long>) {
        val stale=db.rawQuery("SELECT id FROM images",null).use { c -> buildList { while(c.moveToNext()) if(c.getLong(0) !in active) add(c.getLong(0)) } }
        db.beginTransaction(); try {
            stale.forEach { id -> db.execSQL("DELETE FROM images WHERE id=?",arrayOf(id)); db.execSQL("DELETE FROM faces WHERE media=?",arrayOf(id)) }
            db.execSQL("DELETE FROM people WHERE id NOT IN (SELECT person FROM faces)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        val valid=faces().map { "${it.key}.jpg" }.toSet()
        directory.listFiles()?.filter { it.name !in valid }?.forEach { it.delete() }
    }
    fun rename(id: String, name: String) { db.execSQL("UPDATE people SET name=? WHERE id=?",arrayOf(name.trim().take(80).ifEmpty { "Pessoa" },id)) }
    fun merge(from: String, into: String) = merge(setOf(from), into)
    fun merge(from: Set<String>, into: String) {
        val sources=from-into
        require(sources.isNotEmpty()) { "Selecione pelo menos um grupo." }
        db.beginTransaction()
        try {
            for(id in sources+into) {
                require(db.rawQuery("SELECT 1 FROM people WHERE id=?",arrayOf(id)).use { it.moveToFirst() }) { "Um grupo mudou. Abra a seleção novamente." }
            }
            sources.forEach { id ->
                db.execSQL("UPDATE faces SET person=? WHERE person=?",arrayOf(into,id))
                db.execSQL("DELETE FROM people WHERE id=?",arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun separate(key: String) { db.beginTransaction(); try { val id=person(); db.execSQL("UPDATE faces SET person=? WHERE key=?",arrayOf(id,key)); db.setTransactionSuccessful() } finally { db.endTransaction() } }
    suspend fun snapshot(active: Set<Long>): Organization {
        val all=faces().filter { it.mediaId in active }
        val names=db.rawQuery("SELECT id,name FROM people",null).use { c -> buildMap { while(c.moveToNext()) put(c.getString(0),c.getString(1)) } }
        val people=all.groupBy { it.person }.map { (id,f) -> PersonGroup(id,names[id] ?: "Pessoa",f) }.sortedByDescending { it.faces.size }
        val images=db.rawQuery("SELECT id,hash,colors FROM images ORDER BY id",null).use { c -> buildList { while(c.moveToNext()) if(c.getLong(0) in active) add(ImageSignature(c.getLong(0),c.getLong(1),unpack(c.getBlob(2)))) } }
        val groups=mutableListOf<MutableList<ImageSignature>>()
        for(image in images) {
            kotlinx.coroutines.currentCoroutineContext().ensureActiveCompat()
            val match=groups.firstOrNull { group -> val first=group.first(); java.lang.Long.bitCount(first.hash xor image.hash)<=10 && colorDistance(first.colors,image.colors)<.12f }
            if(match==null) groups.add(mutableListOf(image)) else match.add(image)
        }
        return Organization(people,groups.filter { it.size>1 }.map { g -> g.map { it.id } },images.size)
    }
    private fun colorDistance(a: FloatArray,b: FloatArray): Float = a.indices.sumOf { abs(a[it]-b[it]).toDouble() }.toFloat()/a.size
    private fun signature(bitmap: Bitmap,id: Long): ImageSignature {
        val small=Bitmap.createScaledBitmap(bitmap,32,32,true)
        try {
            val gray=DoubleArray(1024); val colors=FloatArray(12)
            for(y in 0..31)for(x in 0..31){val pixel=small.getPixel(x,y);val r=(pixel shr 16) and 255;val g=(pixel shr 8) and 255;val b=pixel and 255;gray[y*32+x]=.299*r+.587*g+.114*b;val cell=(y/16*2+x/16)*3;colors[cell]+=r/65280f;colors[cell+1]+=g/65280f;colors[cell+2]+=b/65280f}
            val dct=DoubleArray(64)
            for(v in 0..7)for(u in 0..7){var sum=0.0;for(y in 0..31)for(x in 0..31)sum+=gray[y*32+x]*cos((2*x+1)*u*PI/64)*cos((2*y+1)*v*PI/64);dct[v*8+u]=sum}
            val median=dct.drop(1).sorted()[31];var hash=0L;for(i in 1..63)if(dct[i]>median)hash=hash or (1L shl i)
            return ImageSignature(id,hash,colors)
        } finally { if(small!==bitmap)small.recycle() }
    }
    override fun close() { helper.close() }
}

private fun kotlin.coroutines.CoroutineContext.ensureActiveCompat() { this[kotlinx.coroutines.Job]?.let { if(!it.isActive)throw kotlinx.coroutines.CancellationException() } }
