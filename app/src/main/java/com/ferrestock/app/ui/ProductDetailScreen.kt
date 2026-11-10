package com.ferrestock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ferrestock.app.AppViewModel
import com.ferrestock.app.data.Movement
import com.ferrestock.app.data.Product
import com.ferrestock.app.data.Repo
import com.ferrestock.app.data.formatDate
import com.ferrestock.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    vm: AppViewModel,
    productId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val product = vm.products.firstOrNull { it.id == productId }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // +1 = ingresar, -1 = retirar, null = cerrado
    var adjusting by rememberSaveable { mutableStateOf<Int?>(null) }
    var history by remember { mutableStateOf<List<Movement>>(emptyList()) }

    LaunchedEffect(productId, product?.quantity, product?.name) {
        runCatching { Repo.productMovements(productId) }.onSuccess { history = it }
    }

    SheetScaffold(
        title = product?.name ?: "Producto",
        subtitle = product?.category?.takeIf { it.isNotBlank() },
        onBack = onBack,
        messages = vm.messages,
        actions = {
            // Editar y eliminar: acciones sensibles, lejos del pulgar.
            if (product != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, "Más opciones", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menuOpen = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = Danger) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Danger) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (product != null) {
                BottomActions {
                    SecondaryButton("Ingresar", { adjusting = 1 }, Modifier.weight(1f), icon = Icons.Filled.Add)
                    // Retirar es lo que más se usa: abajo a la derecha.
                    PrimaryButton(
                        "Retirar", { adjusting = -1 }, Modifier.weight(1f),
                        enabled = product.quantity > 0, icon = Icons.Filled.Remove,
                    )
                }
            }
        },
    ) {
        if (product == null) {
            EmptyState(Icons.Outlined.Inventory2, "Producto no disponible", "Puede que alguien lo haya eliminado.")
            return@SheetScaffold
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductPhoto(product.photoPath, 132.dp, corner = 24.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (product.isLow) DangerSoft else Lime)
                            .padding(16.dp),
                    ) {
                        Text("En stock", style = MaterialTheme.typography.labelMedium, color = if (product.isLow) Danger else Blue)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                product.quantity.toString(),
                                style = MaterialTheme.typography.displaySmall,
                                color = if (product.isLow) Danger else Navy,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                product.unit,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (product.isLow) Danger else Navy,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        if (product.isLow) {
                            Text(
                                if (product.quantity == 0) "Sin stock" else "Stock bajo (mínimo ${product.minStock})",
                                style = MaterialTheme.typography.bodySmall,
                                color = Danger,
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    if (product.description.isNotBlank()) {
                        Text(product.description, style = MaterialTheme.typography.bodyLarge, color = Navy)
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        "Agregado el ${formatDate(product.createdAt)}" +
                            if (product.createdByName.isNotBlank()) " por ${product.createdByName}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
            item { SectionLabel("Movimientos", Modifier.padding(top = 6.dp)) }
            items(history, key = { it.id }) { MovementRow(it, showProduct = false) }
        }
    }

    val sign = adjusting
    if (sign != null && product != null) {
        AdjustSheet(
            product = product,
            sign = sign,
            busy = vm.busy,
            onDismiss = { adjusting = null },
            onConfirm = { qty, note ->
                vm.action({ Repo.adjustStock(product.id, sign * qty, note) }) { adjusting = null }
            },
        )
    }

    if (confirmDelete && product != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color.White,
            title = { Text("¿Eliminar ${product.name}?") },
            text = { Text("Deja de aparecer en el stock. Sus movimientos quedan guardados en el historial.") },
            // El botón destructivo no queda en la posición habitual de "aceptar".
            confirmButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = Blue) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.action({ Repo.deleteProduct(product.id) }) { onBack() }
                }) { Text("Eliminar", color = Danger) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustSheet(
    product: Product,
    sign: Int,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit,
) {
    val withdrawing = sign < 0
    var qty by rememberSaveable { mutableIntStateOf(1) }
    var note by rememberSaveable { mutableStateOf("") }
    val max = if (withdrawing) product.quantity else 9_999_999

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Sheet,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                if (withdrawing) "Retirar stock" else "Ingresar stock",
                style = MaterialTheme.typography.titleLarge,
                color = Navy,
            )
            Text(
                "${product.name} · hay ${product.quantity} ${product.unit}",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            Spacer(Modifier.height(20.dp))
            QuantityStepper(qty, { qty = it }, max)
            Spacer(Modifier.height(20.dp))
            AppTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = if (withdrawing) "¿Para qué se retira?" else "Detalle (opcional)",
                placeholder = if (withdrawing) "Ej.: obra calle San Martín" else "Ej.: compra a proveedor",
                singleLine = false,
                imeAction = ImeAction.Done,
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (withdrawing) "Retirar $qty ${product.unit}" else "Ingresar $qty ${product.unit}",
                onClick = { onConfirm(qty, note.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = qty in 1..max,
                loading = busy,
            )
            if (withdrawing && qty == max) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Es todo el stock disponible.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Blue,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}
