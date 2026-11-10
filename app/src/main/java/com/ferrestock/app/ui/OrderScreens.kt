package com.ferrestock.app.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ferrestock.app.AppViewModel
import com.ferrestock.app.data.Repo
import com.ferrestock.app.data.formatDateTime
import com.ferrestock.app.data.search
import com.ferrestock.app.pdf.OrderPdf
import com.ferrestock.app.ui.theme.*
import com.ferrestock.app.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(vm: AppViewModel, onBack: () -> Unit, onCreated: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var confirming by rememberSaveable { mutableStateOf(false) }

    // Si un producto se eliminó o bajó su stock mientras se armaba la orden, se ajusta el carrito.
    LaunchedEffect(vm.products) {
        val byId = vm.products.associateBy { it.id }
        vm.cart.keys.toList().forEach { id ->
            val stock = byId[id]?.quantity ?: 0
            val wanted = vm.cart[id] ?: 0
            if (stock <= 0) vm.cart.remove(id) else if (wanted > stock) vm.cart[id] = stock
        }
    }

    val cartCount = vm.cart.size
    val cartUnits = vm.cart.values.sum()
    // En la búsqueda vacía, lo que ya está en la orden va primero.
    val visible = remember(vm.products, query, cartCount) {
        val found = vm.products.search(query)
        if (query.isBlank()) found.sortedByDescending { it.id in vm.cart } else found
    }

    SheetScaffold(
        title = "Nueva orden",
        subtitle = "Elegí productos y cantidades",
        onBack = onBack,
        messages = vm.messages,
        bottomBar = {
            // Buscador abajo, al alcance del pulgar; con el teclado abierto sube pegado a él y
            // el resumen de la orden se esconde para dejar lugar a la lista.
            val typing = imeVisible()
            Column(Modifier.imePadding()) {
            BottomSearchRow(query, { query = it }, "Buscar producto…")
            if (!typing) BottomActions {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (cartCount == 1) "1 producto" else "$cartCount productos",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text("$cartUnits en total", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
                PrimaryButton("Continuar", { confirming = true }, Modifier.weight(1f), enabled = cartCount > 0)
            }
            }
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (visible.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.SearchOff, "Sin resultados", "No hay productos que coincidan con la búsqueda.")
                }
            }
            items(visible, key = { it.id }) { product ->
                val inCart = vm.cart[product.id] ?: 0
                AppCard {
                    ProductPhoto(product.photoPath, 52.dp, corner = 14.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            product.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Navy,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (product.quantity == 0) "Sin stock" else "Hay ${product.quantity} ${product.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (product.quantity == 0) Danger else Muted,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    MiniStepper(inCart, product.quantity) { n ->
                        if (n <= 0) vm.cart.remove(product.id) else vm.cart[product.id] = n
                    }
                }
            }
        }
    }

    if (confirming) {
        ModalBottomSheet(
            onDismissRequest = { confirming = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Sheet,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            ) {
                Text("Confirmar orden", style = MaterialTheme.typography.titleLarge, color = Navy)
                Text(
                    "Se descuentan $cartUnits unidades de ${if (cartCount == 1) "1 producto" else "$cartCount productos"}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Spacer(Modifier.height(16.dp))
                AppTextField(
                    value = description,
                    onValueChange = { description = it.take(300) },
                    label = "Descripción / destino",
                    placeholder = "Ej.: materiales para obra Rivadavia 1200",
                    singleLine = false,
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    text = "Guardar orden",
                    onClick = {
                        val items = vm.cart.toMap()
                        vm.action({ Repo.createOrder(description.trim(), items) }) { orderId ->
                            vm.cart.clear()
                            confirming = false
                            onCreated(orderId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    loading = vm.busy,
                )
            }
        }
    }
}

@Composable
fun OrderDetailScreen(vm: AppViewModel, orderId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val order = vm.orders.firstOrNull { it.id == orderId }
    var exporting by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    SheetScaffold(
        title = order?.code ?: "Orden",
        subtitle = "Orden de retiro",
        onBack = onBack,
        messages = vm.messages,
        actions = {
            // Eliminar es sensible: arriba a la derecha, lejos del pulgar.
            if (order != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, "Más opciones", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Eliminar orden", color = Danger) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Danger) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (order != null) {
                BottomActions {
                    PrimaryButton(
                        text = "Exportar PDF",
                        icon = Icons.Outlined.PictureAsPdf,
                        loading = exporting,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            exporting = true
                            scope.launch {
                                try {
                                    val file = withContext(Dispatchers.IO) { OrderPdf.create(context, order) }
                                    val uri = FileProvider.getUriForFile(context, Images.authority(context), file)
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "Orden ${order.code}")
                                        clipData = ClipData.newRawUri(file.name, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Compartir ${order.code}"))
                                } catch (e: Exception) {
                                    vm.messages.tryEmit("No se pudo generar el PDF.")
                                } finally {
                                    exporting = false
                                }
                            }
                        },
                    )
                }
            }
        },
    ) {
        if (order == null) {
            EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, "Orden no disponible", "Deslizá hacia abajo en la lista de órdenes para actualizar.")
            return@SheetScaffold
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Lime)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Responsable", style = MaterialTheme.typography.labelMedium, color = Blue)
                            Text(order.userName, style = MaterialTheme.typography.titleMedium, color = Navy)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Fecha", style = MaterialTheme.typography.labelMedium, color = Blue)
                            Text(formatDateTime(order.createdAt), style = MaterialTheme.typography.titleSmall, color = Navy)
                        }
                    }
                    if (order.description.isNotBlank()) {
                        Column {
                            Text("Descripción", style = MaterialTheme.typography.labelMedium, color = Blue)
                            Text(order.description, style = MaterialTheme.typography.bodyLarge, color = Navy)
                        }
                    }
                }
            }
            item {
                SectionLabel(
                    if (order.items.size == 1) "1 producto" else "${order.items.size} productos",
                    Modifier.padding(top = 8.dp),
                )
            }
            items(order.items, key = { it.id }) { item ->
                AppCard {
                    Text(
                        item.productName,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Navy,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${item.quantity} ${item.unit}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Navy,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Lime)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (confirmDelete && order != null) {
        val active = remember(vm.products) { vm.products.map { it.id }.toSet() }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color.White,
            title = { Text("¿Eliminar ${order.code}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Lo importante, resaltado: el stock vuelve.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Lime)
                            .border(1.5.dp, Teal, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Restore, null, tint = Teal, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "El stock que usó esta orden vuelve al inventario",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Navy,
                            )
                        }
                        order.items.forEach { item ->
                            val restorable = item.productId in active
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (restorable) "+${item.quantity} ${item.unit}" else "—",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (restorable) Teal else Muted,
                                    modifier = Modifier.widthIn(min = 56.dp),
                                )
                                Text(
                                    if (restorable) item.productName else "${item.productName} (producto eliminado, no se devuelve)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (restorable) Navy else Muted,
                                )
                            }
                        }
                    }
                    Text(
                        "La orden deja de aparecer en la lista. En el historial queda registrado quién la eliminó.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            },
            // El botón destructivo no queda en la posición habitual de "aceptar".
            confirmButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = Blue) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.action({ Repo.deleteOrder(order.id) }) { onBack() }
                }) { Text("Eliminar y devolver stock", color = Danger) }
            },
        )
    }
}
