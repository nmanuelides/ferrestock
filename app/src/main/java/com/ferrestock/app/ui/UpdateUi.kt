package com.ferrestock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ferrestock.app.BuildConfig
import com.ferrestock.app.update.PendingBuild
import com.ferrestock.app.update.UpdateState
import com.ferrestock.app.update.UpdateViewModel
import com.ferrestock.app.ui.theme.*
import kotlinx.coroutines.delay

/** Aviso arriba de la lista cuando hay una versión nueva (o se está descargando). */
@Composable
fun UpdateBanner(state: UpdateState, onInstall: () -> Unit) {
    when (state) {
        is UpdateState.Available -> Row(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .fillMaxWidth()
                .background(Lime, RoundedCornerShape(16.dp))
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.SystemUpdate, null, tint = Navy)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Hay una versión nueva", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Navy)
                Text(state.name, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Button(
                onClick = onInstall,
                colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Actualizar") }
        }

        is UpdateState.Downloading -> Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth()) {
            Text("Descargando actualización… ${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), color = Teal, trackColor = Line)
        }

        is UpdateState.Failed -> Text(
            "No se pudo actualizar: ${state.message}",
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            color = Danger,
            style = MaterialTheme.typography.bodySmall,
        )

        // Una versión compilándose solo se muestra en "Buscar actualizaciones".
        UpdateState.None, is UpdateState.Building -> Unit
    }
}

/** Diálogo de "Buscar actualizaciones": versión instalada, chequeo en el momento y resultado. */
@Composable
fun UpdateDialog(updates: UpdateViewModel, onDismiss: () -> Unit) {
    var checking by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        updates.checkNow()
        checking = false
    }
    val state = updates.state

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = { Icon(Icons.Filled.SystemUpdate, null, tint = Teal) },
        title = { Text("Actualizaciones") },
        text = {
            Column {
                Text("Versión instalada: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = Muted)
                Spacer(Modifier.height(16.dp))
                when {
                    checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Teal, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Buscando…")
                    }
                    state is UpdateState.Available -> Text("${state.name} está disponible.", color = Navy, fontWeight = FontWeight.SemiBold)
                    state is UpdateState.Downloading -> Column {
                        Text("Descargando… ${(state.progress * 100).toInt()}%")
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), color = Teal, trackColor = Line)
                    }
                    state is UpdateState.Building -> Column {
                        val progress = buildProgress(state.build)
                        Text("Se está preparando una versión nueva: ${(progress * 100).toInt()}%")
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Blue, trackColor = Line)
                        Spacer(Modifier.height(6.dp))
                        Text("Cuando esté lista vas a poder instalarla desde acá.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    state is UpdateState.Failed -> Text("No se pudo actualizar: ${state.message}", color = Danger)
                    else -> Text("Tenés la última versión.", color = Navy)
                }
            }
        },
        confirmButton = {
            if (!checking && state is UpdateState.Available) {
                TextButton(onClick = updates::install) { Text("Instalar", color = Teal, fontWeight = FontWeight.Bold) }
            } else {
                TextButton(onClick = onDismiss) { Text("Cerrar", color = Blue) }
            }
        },
        dismissButton = if (!checking && state is UpdateState.Available) {
            { TextButton(onClick = onDismiss) { Text("Ahora no", color = Muted) } }
        } else null,
    )
}

/** Progreso estimado de la compilación, actualizado cada segundo. */
@Composable
private fun buildProgress(build: PendingBuild): Float {
    var progress by remember(build) { mutableFloatStateOf(build.progress()) }
    LaunchedEffect(build) {
        while (true) {
            progress = build.progress()
            delay(1_000L)
        }
    }
    return progress
}
