package com.gal.localweb

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gal.data.local.PrivateMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun NotebookConnection() {
    val colors = MaterialTheme.colorScheme
    val theme = mapOf(
        "background" to colors.background, "surface" to colors.surfaceContainer,
        "container" to colors.secondaryContainer, "outline" to colors.outlineVariant,
        "primary" to colors.primary, "on-primary" to colors.onPrimary,
        "text" to colors.onSurface, "muted" to colors.onSurfaceVariant,
    ).mapValues { (_, color) -> "#%06x".format(color.toArgb() and 0xffffff) }
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf<NotebookServer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var peer by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        enabled = allowed
        if (!allowed) error = "Autorize o acesso à rede local para conectar."
    }
    DisposableEffect(enabled) {
        if (enabled) {
            val store = PrivateMediaStore(context.applicationContext)
            try {
                val address = NotebookServer.localAddress() ?: error("Conecte o celular ao Wi-Fi.")
                server = NotebookServer(context.applicationContext, store, address, theme).apply { start(30_000, true) }
                view.keepScreenOn = true
            } catch (failure: Exception) { error = failure.message; enabled = false; store.close() }
            onDispose { server?.stop(); server = null; store.close(); view.keepScreenOn = false }
        } else onDispose { }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) enabled = false }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(server) { while (server != null) { peer = server?.pendingPeer; delay(300) } }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Conectar ao notebook", style = MaterialTheme.typography.titleMedium)
        Text("Use a mesma rede Wi-Fi de confiança. Mantenha esta tela aberta; sair ou bloquear o celular encerra o acesso. Sessão de até 1 hora, sem nuvem.")
        Button(onClick = {
            error = null
            if (enabled) enabled = false
            else if (Build.VERSION.SDK_INT >= 37 && context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) permission.launch("android.permission.ACCESS_LOCAL_NETWORK")
            else enabled = true
        }) { Text(if (enabled) "Encerrar conexão" else "Iniciar conexão") }
        server?.let { current ->
            SelectionContainer { Text("Abra no navegador do notebook:\n${current.url}\n\nCódigo: ${current.code}", style = MaterialTheme.typography.bodyLarge) }
            Text("HTTP local: o tráfego não é criptografado. Autorize somente o seu notebook.", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (peer != null && server != null) AlertDialog(
        onDismissRequest = { server?.approve(false); peer = null },
        title = { Text("Autorizar notebook?") },
        text = { Text("O dispositivo $peer informou o código. Permitir visualizar, adicionar, reposicionar e enviar mídias à lixeira?") },
        confirmButton = { TextButton(onClick = { server?.approve(true); peer = null }) { Text("Autorizar") } },
        dismissButton = { TextButton(onClick = { server?.approve(false); peer = null }) { Text("Recusar") } },
    )
}
