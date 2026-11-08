package com.gal.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gal.model.Media
import com.gal.ui.actions.MediaActionsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun RepositionDialog(
    media: Media,
    actions: MediaActionsViewModel,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    selectedIds: Set<Long> = setOf(media.id),
) {
    val library by actions.library.collectAsStateWithLifecycle(initialValue = emptyList())
    val current = library.firstOrNull { it.id == media.id }
    var input by remember(media.id) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val target = input.toIntOrNull()
    val maximum = library.size - selectedIds.size + 1
    val valid = current != null && selectedIds.all { id -> library.any { it.id == id } } && target != null && target in 1..maximum
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Reposicionar") },
        text = {
            Column {
                Text("Posição atual: ${current?.position ?: media.position ?: "—"}. A posição 1 fica embaixo.")
                if (selectedIds.size > 1) Text("${selectedIds.size} itens: o �ltimo na ordem da galeria (o mais abaixo) ficar� na posi��o digitada. A ordem do grupo ser� preservada.")
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    enabled = !saving,
                    singleLine = true,
                    label = { Text("Nova posição") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = error != null || (input.isNotEmpty() && !valid),
                    supportingText = { Text(error ?: "Digite um número de 1 a ${maximum}.") },
                )
                Text("Os itens entre as duas posições serão deslocados automaticamente.")
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !saving, onClick = {
                val position = target ?: return@TextButton
                saving = true
                scope.launch {
                    try {
                        actions.reposition(selectedIds, position)
                        onDone()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message ?: "Não foi possível reposicionar."
                    } finally { saving = false }
                }
            }) { Text(if (saving) "Salvando…" else "Reposicionar") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
