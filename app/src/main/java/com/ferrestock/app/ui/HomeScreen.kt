package com.ferrestock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ferrestock.app.AppViewModel
import com.ferrestock.app.BuildConfig
import com.ferrestock.app.data.Repo
import com.ferrestock.app.data.StockOrder
import com.ferrestock.app.data.formatDateTime
import com.ferrestock.app.data.search
import com.ferrestock.app.ui.theme.*
import com.ferrestock.app.update.UpdateViewModel
import kotlinx.coroutines.launch

private val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 104.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    updates: UpdateViewModel,
    onOpenProduct: (String) -> Unit,
    onAddProduct: () -> Unit,
    onNewOrder: () -> Unit,
    onOpenOrder: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var updateDialog by remember { mutableStateOf(false) }

    if (updateDialog) UpdateDialog(updates) { updateDialog = false }

    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmDelete = false },
            containerColor = Color.White,
            icon = { Icon(Icons.Outlined.PersonRemove, null, tint = Danger) },
            title = { Text("¿Eliminar tu cuenta?") },
            text = {
                Text(
                    "Perdés el acceso a Ferrestock y se cierra la sesión en todos tus dispositivos. " +
                        "Lo que hiciste queda en el historial con tu nombre, como registro del stock. " +
                        "Para volver a entrar, alguien con acceso al panel tiene que reactivar tu cuenta."
                )
            },
            // El botón destructivo no queda en la posición habitual de "aceptar".
            confirmButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !deleting) { Text("Cancelar", color = Blue) }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            runCatching { Repo.deleteAccount(context) }
                                .onSuccess { vm.clearSession() }
                                .onFailure { vm.messages.tryEmit(AppViewModel.friendly(it)) }
                            deleting = false
                            confirmDelete = false
                        }
                    },
                ) {
                    if (deleting) CircularProgressIndicator(Modifier.size(18.dp), color = Danger, strokeWidth = 2.dp)
                    else Text("Eliminar cuenta", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    LaunchedEffect(Unit) { vm.refresh() }

    val firstName = vm.userName.substringBefore(' ').ifBlank { "¡hola!" }

    SheetScaffold(
        title = when (tab) {
            0 -> "Productos"
            1 -> "Órdenes"
            else -> "Historial"
        },
        subtitle = if (Repo.demo) "Modo demo · datos de ejemplo" else "Hola, $firstName",
        messages = vm.messages,
        actions = {
            // Cuenta y cierre de sesión: poco usado, va arriba a la derecha.
            Box {
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Lime)
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        vm.userName.take(1).uppercase().ifBlank { "?" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Navy,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(vm.userName, style = MaterialTheme.typography.titleSmall, color = Navy)
                        Text(vm.userEmail, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    HorizontalDivider(color = Line)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Buscar actualizaciones")
                                Text("Versión ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        },
                        leadingIcon = { Icon(Icons.Filled.SystemUpdate, null) },
                        onClick = {
                            menuOpen = false
                            updateDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (Repo.demo) "Reiniciar datos de ejemplo" else "Cerrar sesión") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                        onClick = {
                            menuOpen = false
                            scope.launch {
                                runCatching { Repo.signOut(context) }
                                vm.clearSession()
                                if (Repo.demo) vm.refresh()
                            }
                        },
                    )
                    if (!Repo.demo) {
                        DropdownMenuItem(
                            text = { Text("Eliminar cuenta", color = Danger) },
                            leadingIcon = { Icon(Icons.Outlined.PersonRemove, null, tint = Danger) },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                }
            }
        },
        // La hoja pasa del verde clarito (arriba) al verde de la barra (abajo), sin corte.
        sheetBrush = Brush.verticalGradient(
            0f to Lime,
            0.5f to lerp(Lime, Teal, 0.18f),
            1f to Teal,
        ),
        headerExtra = if (tab == 0) {
            { SearchField(query, { query = it }, "Buscar producto…") }
        } else null,
        bottomBar = {
            NavigationBar(containerColor = Teal, tonalElevation = 0.dp) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Navy,
                    selectedTextColor = Color.White,
                    indicatorColor = Lime,
                    unselectedIconColor = Color.White.copy(alpha = 0.85f),
                    unselectedTextColor = Color.White.copy(alpha = 0.85f),
                )
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 }, colors = colors,
                    icon = { Icon(Icons.Outlined.Inventory2, null) }, label = { Text("Productos") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 }, colors = colors,
                    icon = { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null) }, label = { Text("Órdenes") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 }, colors = colors,
                    icon = { Icon(Icons.Outlined.History, null) }, label = { Text("Historial") },
                )
            }
        },
        fab = {
            // Acción más usada de cada pestaña: abajo a la derecha.
            when (tab) {
                0 -> ExtendedFloatingActionButton(
                    onClick = onAddProduct,
                    containerColor = Navy,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Agregar", style = MaterialTheme.typography.labelLarge) },
                )
                1 -> ExtendedFloatingActionButton(
                    onClick = onNewOrder,
                    containerColor = Navy,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Nueva orden", style = MaterialTheme.typography.labelLarge) },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
        UpdateBanner(updates.state, updates::install)
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            when (tab) {
                0 -> {
                    val filtered = remember(vm.products, query) { vm.products.search(query) }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (filtered.isEmpty() && vm.loaded) {
                            item {
                                if (query.isBlank()) {
                                    EmptyState(Icons.Outlined.Inventory2, "Todavía no hay productos", "Tocá «Agregar» para cargar el primero.")
                                } else {
                                    EmptyState(Icons.Outlined.SearchOff, "Sin resultados", "No hay productos que coincidan con «$query».")
                                }
                            }
                        }
                        items(filtered, key = { it.id }) { product ->
                            ProductRow(product) { onOpenProduct(product.id) }
                        }
                    }
                }

                1 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (vm.orders.isEmpty() && vm.loaded) {
                        item {
                            EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, "Todavía no hay órdenes", "Una orden descuenta varios productos juntos y se puede exportar en PDF.")
                        }
                    }
                    items(vm.orders, key = { it.id }) { order ->
                        OrderRow(order) { onOpenOrder(order.id) }
                    }
                }

                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (vm.movements.isEmpty() && vm.loaded) {
                        item {
                            EmptyState(Icons.Outlined.History, "Sin movimientos", "Acá va a quedar registrado quién ingresó o retiró cada producto.")
                        }
                    }
                    items(vm.movements, key = { it.id }) { movement ->
                        MovementRow(movement)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun OrderRow(order: StockOrder, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Lime),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, tint = Teal)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(order.code, style = MaterialTheme.typography.titleMedium, color = Navy)
            if (order.description.isNotBlank()) {
                Text(
                    order.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Blue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${order.userName} · ${formatDateTime(order.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (order.items.size == 1) "1 ítem" else "${order.items.size} ítems",
            style = MaterialTheme.typography.labelMedium,
            color = Navy,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Lime)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
