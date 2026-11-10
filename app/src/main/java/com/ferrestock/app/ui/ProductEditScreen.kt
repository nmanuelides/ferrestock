package com.ferrestock.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ferrestock.app.AppViewModel
import com.ferrestock.app.data.Repo
import com.ferrestock.app.ui.theme.Muted
import com.ferrestock.app.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Alta (productId == null) o edición de un producto. */
@Composable
fun ProductEditScreen(vm: AppViewModel, productId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(productId, vm.products) { vm.products.firstOrNull { it.id == productId } }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "") }
    var unit by rememberSaveable { mutableStateOf(existing?.unit ?: "u") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var minStock by rememberSaveable { mutableStateOf(existing?.minStock?.takeIf { it > 0 }?.toString() ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingUnit by remember { mutableStateOf(false) }
    // Unidades creadas en esta pantalla, antes de guardar el producto.
    var createdUnits by rememberSaveable { mutableStateOf(listOf<String>()) }
    val units = remember(vm.products, createdUnits, unit) {
        unitOptions(vm.products.map { it.unit } + createdUnits, unit)
    }
    // Mientras el selector está abierto, la pantalla de atrás se desenfoca.
    val backgroundBlur by animateDpAsState(if (pickingUnit) 16.dp else 0.dp, label = "unitBlur")

    if (pickingUnit) {
        UnitPickerDialog(
            options = units,
            selected = unit,
            onSelect = { chosen ->
                if (units.none { it.equals(chosen, ignoreCase = true) }) createdUnits = createdUnits + chosen
                unit = chosen
                pickingUnit = false
            },
            onDismiss = { pickingUnit = false },
        )
    }

    fun usePhoto(uri: Uri) {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { Images.compress(context, uri) }
            if (bytes != null) vm.pendingPhoto = bytes else vm.messages.tryEmit("No se pudo leer la foto.")
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) usePhoto(Uri.parse(uri))
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) usePhoto(uri)
    }

    val preview = remember(vm.pendingPhoto) {
        vm.pendingPhoto?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }

    SheetScaffold(
        title = if (existing == null) "Nuevo producto" else "Editar producto",
        onBack = onDone,
        messages = vm.messages,
        modifier = Modifier.blur(backgroundBlur),
        bottomBar = {
            BottomActions {
                PrimaryButton(
                    text = if (existing == null) "Guardar producto" else "Guardar cambios",
                    onClick = {
                        vm.action({
                            Repo.saveProduct(
                                existing = existing,
                                name = name.trim(),
                                description = description.trim(),
                                category = category.trim(),
                                unit = unit.trim().ifBlank { "u" },
                                quantity = quantity.toIntOrNull() ?: 0,
                                minStock = minStock.toIntOrNull() ?: 0,
                                newPhoto = vm.pendingPhoto,
                            )
                        }) {
                            vm.pendingPhoto = null
                            onDone()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank(),
                    loading = vm.busy,
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = "Foto del producto",
                        modifier = Modifier
                            .size(132.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    ProductPhoto(existing?.photoPath, 132.dp, corner = 24.dp)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        "Sacar foto",
                        onClick = {
                            val uri = Images.newCameraUri(context)
                            cameraUri = uri.toString()
                            runCatching { takePicture.launch(uri) }
                                .onFailure { vm.messages.tryEmit("No se encontró una app de cámara.") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.PhotoCamera,
                    )
                    SecondaryButton(
                        "Galería",
                        onClick = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.PhotoLibrary,
                    )
                }
            }

            AppTextField(name, { name = it.take(80) }, "Nombre", placeholder = "Ej.: Plafón de luz LED 18W")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (existing == null) {
                    AppTextField(quantity, { quantity = it }, "Cantidad inicial", Modifier.weight(1f), number = true)
                }
                UnitField(unit, onClick = { pickingUnit = true }, modifier = Modifier.weight(1f))
            }

            AppTextField(category, { category = it.take(40) }, "Categoría (opcional)", placeholder = "Ej.: Iluminación")
            AppTextField(minStock, { minStock = it }, "Avisar cuando queden… (opcional)", number = true)
            AppTextField(
                description, { description = it.take(300) }, "Descripción (opcional)",
                singleLine = false, imeAction = ImeAction.Done,
            )

            if (existing == null) {
                Text(
                    "La fecha de alta y tu nombre se registran automáticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }
    }
}
