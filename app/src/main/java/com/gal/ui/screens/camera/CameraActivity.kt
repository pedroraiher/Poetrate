package com.gal.ui.screens.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gal.data.local.PrivateMediaStore
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject

/** CameraX writes directly to internal files, without delegating to another camera app. */
@AndroidEntryPoint
class CameraActivity : ComponentActivity() {
    @Inject lateinit var store: PrivateMediaStore
    private lateinit var preview: PreviewView
    private lateinit var shutter: Button
    private lateinit var mode: Button
    private lateinit var flip: Button
    private lateinit var status: TextView
    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var video = false
    private var front = false
    private var busy = false
    private val writer = Executors.newSingleThreadExecutor()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (granted(Manifest.permission.CAMERA)) bindCamera()
        else {
            status.text = "Permita o acesso à câmera para capturar."
            shutter.text = "Permitir câmera"
            shutter.isEnabled = true
        }
    }
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) startVideo() else toast("Microfone negado. Permita-o para gravar vídeo com áudio.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        video = savedInstanceState?.getBoolean("video") ?: false
        front = savedInstanceState?.getBoolean("front") ?: false
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        status = TextView(this).apply {
            text = "Capturas privadas — salvas apenas neste aplicativo"
            setTextColor(android.graphics.Color.WHITE)
            setPadding(24, 16, 24, 16)
        }
        layout.addView(status)
        preview = PreviewView(this)
        layout.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this)
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { action() }
            controls.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
        }
        mode = button("Vídeo") { video = !video; bindCamera() }
        shutter = button("Capturar") {
            if (!granted(Manifest.permission.CAMERA)) requestCamera()
            else if (recording != null) { shutter.isEnabled = false; recording?.stop() }
            else if (video) {
                if (granted(Manifest.permission.RECORD_AUDIO)) startVideo()
                else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            } else takePhoto()
        }
        flip = button("Virar") { front = !front; bindCamera() }
        layout.addView(controls)
        layout.addView(Button(this).apply { text = "Voltar à biblioteca"; setOnClickListener { leave() } })
        setContentView(layout)
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { leave() }
        })
        if (granted(Manifest.permission.CAMERA)) bindCamera() else requestCamera()
    }

    private fun granted(name: String) = ContextCompat.checkSelfPermission(this, name) == PackageManager.PERMISSION_GRANTED
    private fun requestCamera() = permission.launch(arrayOf(Manifest.permission.CAMERA))
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun controls(enabled: Boolean) {
        shutter.isEnabled = enabled; mode.isEnabled = enabled; flip.isEnabled = enabled
    }

    private fun bindCamera() {
        controls(false)
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isDestroyed || isFinishing) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                var selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                if (!cameraProvider.hasCamera(selector)) {
                    front = !front
                    selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                }
                val viewfinder = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                cameraProvider.unbindAll()
                imageCapture = null; videoCapture = null
                if (video) {
                    val capture = VideoCapture.withOutput(Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HIGHEST)).build())
                    videoCapture = capture
                    cameraProvider.bindToLifecycle(this, selector, viewfinder, capture)
                } else {
                    val capture = ImageCapture.Builder().build()
                    imageCapture = capture
                    cameraProvider.bindToLifecycle(this, selector, viewfinder, capture)
                }
                mode.text = if (video) "Foto" else "Vídeo"
                shutter.text = if (video) "Gravar" else "Fotografar"
                status.text = "Capturas privadas — salvas apenas neste aplicativo"
                controls(true)
            } catch (e: Exception) {
                status.text = "Não foi possível abrir a câmera. Volte e tente novamente."
                toast(e.localizedMessage ?: "Câmera indisponível")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val file = runCatching { store.newCapture(false) }.getOrElse { toast("Sem espaço para salvar a foto"); return }
        capture.targetRotation = preview.display.rotation
        busy = true; controls(false)
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), writer,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) { save(file, false) }
                override fun onError(error: ImageCaptureException) {
                    file.delete()
                    runOnUiThread {
                        busy = false
                        if (isDestroyed) writer.shutdown()
                        else { controls(true); toast("Falha ao capturar a foto") }
                    }
                }
            })
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startVideo() {
        if (!granted(Manifest.permission.RECORD_AUDIO) || busy) return
        val capture = videoCapture ?: return
        val file = runCatching { store.newCapture(true) }.getOrElse { toast("Sem espaço para gravar"); return }
        capture.targetRotation = preview.display.rotation
        busy = true; mode.isEnabled = false; flip.isEnabled = false
        try {
            recording = capture.output.prepareRecording(this, FileOutputOptions.Builder(file).build())
                .withAudioEnabled().start(ContextCompat.getMainExecutor(this)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> { shutter.text = "Parar"; status.text = "Gravando…" }
                        is VideoRecordEvent.Status -> {
                            status.text = "Gravando: ${event.recordingStats.recordedDurationNanos / 1_000_000_000}s"
                        }
                        is VideoRecordEvent.Finalize -> {
                            recording?.close(); recording = null
                            shutter.isEnabled = false
                            if (!event.hasError()) writer.execute { save(file, true) }
                            else {
                                file.delete(); busy = false; controls(true); shutter.text = "Gravar"
                                toast("Gravação interrompida; o vídeo não foi salvo (${event.error}).")
                                if (isDestroyed) writer.shutdown()
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            file.delete(); busy = false; controls(true); toast("Não foi possível iniciar a gravação")
        }
    }

    /** Runs on the file executor, which is allowed to finish a save after leaving the screen. */
    private fun save(file: File, isVideo: Boolean) {
        val ok = runCatching {
            store.commit(file, if (isVideo) "video/mp4" else "image/jpeg",
                "${if (isVideo) "VID" else "IMG"}_${System.currentTimeMillis()}.${if (isVideo) "mp4" else "jpg"}")
        }.isSuccess
        file.delete()
        runOnUiThread {
            busy = false
            if (isDestroyed) writer.shutdown()
            if (!isDestroyed) {
                controls(true); shutter.text = if (video) "Gravar" else "Fotografar"
                status.text = if (ok) "Salvo na biblioteca privada" else "Falha ao salvar"
                toast(status.text.toString())
            }
        }
    }

    private fun leave() {
        if (busy) {
            recording?.stop()
            toast("Aguarde o término do salvamento")
        } else finish()
    }
    override fun onStop() { recording?.stop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("video", video); outState.putBoolean("front", front)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        recording?.close()
        provider?.unbindAll()
        // Finalize can arrive after onDestroy; keep its executor alive until the callback finishes.
        if (!busy) writer.shutdown()
        super.onDestroy()
    }
}
