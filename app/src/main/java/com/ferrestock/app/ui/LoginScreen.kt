package com.ferrestock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.ferrestock.app.AppViewModel
import com.ferrestock.app.data.Repo
import com.ferrestock.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
private fun Logo() {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(Blue.copy(alpha = 0.55f))
        )
        Box(
            Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(Lime),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Inventory2, null, tint = Navy, modifier = Modifier.size(76.dp))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 22.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Teal)
        )
    }
}

@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Navy)
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Logo()
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Teal)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("FERRESTOCK", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "El stock del depósito, siempre al día y con nombre y apellido en cada movimiento.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            PrimaryButton(
                text = "Continuar con Google",
                onClick = {
                    error = null
                    loading = true
                    scope.launch {
                        try {
                            Repo.signInWithGoogle(context)
                        } catch (_: GetCredentialCancellationException) {
                            // El usuario cerró el selector de cuentas: no es un error.
                        } catch (e: Exception) {
                            error = AppViewModel.friendly(e)
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                loading = loading,
                container = Navy,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                error ?: "Si es tu primera vez, la cuenta se crea sola.",
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) Lime else Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Navy),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Lime)
    }
}
