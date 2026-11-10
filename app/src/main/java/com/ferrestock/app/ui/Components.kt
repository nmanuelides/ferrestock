package com.ferrestock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ferrestock.app.data.Movement
import com.ferrestock.app.data.Product
import com.ferrestock.app.data.Repo
import com.ferrestock.app.data.formatDateTime
import com.ferrestock.app.ui.theme.*
import kotlinx.coroutines.flow.Flow

/**
 * Estructura base de todas las pantallas: encabezado azul marino arriba y una
 * "hoja" clara con esquinas redondeadas que sube desde abajo.
 */
@Composable
fun SheetScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    messages: Flow<String>? = null,
    actions: @Composable RowScope.() -> Unit = {},
    headerExtra: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    /** Fondo de la hoja en vez del color liso (la home usa un degradé hacia la barra verde). */
    sheetBrush: Brush? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    if (messages != null) {
        LaunchedEffect(messages) { messages.collect { snackbar.showSnackbar(it) } }
    }

    Scaffold(
        containerColor = Navy,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = bottomBar,
        floatingActionButton = fab,
        topBar = {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Navy)
                        }
                        Spacer(Modifier.width(14.dp))
                    } else {
                        Spacer(Modifier.width(4.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Lime,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    actions()
                }
                if (headerExtra != null) {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.padding(end = 8.dp)) { headerExtra() }
                }
            }
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = Sheet,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (sheetBrush != null) Modifier.background(sheetBrush) else Modifier)
                    .padding(bottom = padding.calculateBottomPadding()),
                content = content,
            )
        }
    }
}

/** Barra inferior para las acciones principales, al alcance del pulgar. */
@Composable
fun BottomActions(content: @Composable RowScope.() -> Unit) {
    Surface(color = Navy) {
        CompositionLocalProvider(LocalOnDark provides true) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        }
    }
}

/** true dentro de superficies oscuras (barra de acciones), para que los botones contrasten. */
val LocalOnDark = staticCompositionLocalOf { false }

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    container: Color = Teal,
    contentColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor,
            disabledContainerColor = container.copy(alpha = 0.45f),
            disabledContentColor = contentColor.copy(alpha = 0.8f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = contentColor, strokeWidth = 2.5.dp)
        } else {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (LocalOnDark.current) Lime else Blue),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (LocalOnDark.current) Lime else Blue),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    number: Boolean = false,
    placeholder: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(if (number) v.filter(Char::isDigit).take(7) else v) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Muted) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        maxLines = if (singleLine) 1 else 4,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (singleLine) imeAction else ImeAction.Default,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = Teal,
            unfocusedBorderColor = Line,
            focusedLabelColor = Teal,
            unfocusedLabelColor = Muted,
            cursorColor = Teal,
        ),
    )
}

/** Buscador para el encabezado oscuro. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = Muted) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Blue) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, "Borrar búsqueda", tint = Muted)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Teal,
        ),
    )
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, enabled: Boolean, size: Dp, filled: Boolean, onClick: () -> Unit) {
    val bg = when {
        !enabled -> Line.copy(alpha = 0.5f)
        filled -> Teal
        else -> Lime
    }
    val fg = when {
        !enabled -> Muted.copy(alpha = 0.6f)
        filled -> Color.White
        else -> Navy
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

/** Selector de cantidad grande, con el número editable a mano. */
@Composable
fun QuantityStepper(value: Int, onChange: (Int) -> Unit, max: Int = 9_999_999) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        RoundIconButton(Icons.Filled.Remove, "Menos", value > 1, 56.dp, false) { onChange(value - 1) }
        OutlinedTextField(
            value = if (value == 0) "" else value.toString(),
            onValueChange = { text ->
                val n = text.filter(Char::isDigit).take(7).toIntOrNull() ?: 0
                onChange(n.coerceAtMost(max))
            },
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(132.dp),
            textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center, color = Navy),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Teal,
                unfocusedBorderColor = Line,
                cursorColor = Teal,
            ),
        )
        RoundIconButton(Icons.Filled.Add, "Más", value < max, 56.dp, true) { onChange(value + 1) }
    }
}

/** Selector compacto para las filas de la orden. */
@Composable
fun MiniStepper(value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (value > 0) {
            RoundIconButton(Icons.Filled.Remove, "Menos", true, 40.dp, false) { onChange(value - 1) }
            Text(
                value.toString(),
                modifier = Modifier.widthIn(min = 40.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = Navy,
            )
        }
        RoundIconButton(Icons.Filled.Add, "Más", value < max, 40.dp, true) { onChange(value + 1) }
    }
}

@Composable
fun ProductPhoto(photoPath: String?, size: Dp, corner: Dp = 16.dp) {
    val url = remember(photoPath) { Repo.photoUrl(photoPath) }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Lime),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Inventory2, null, tint = Teal, modifier = Modifier.size(size * 0.42f))
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun StockBadge(product: Product) {
    val low = product.isLow
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (low) DangerSoft else Lime)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .widthIn(min = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            product.quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (low) Danger else Navy,
        )
        Text(product.unit, style = MaterialTheme.typography.labelSmall, color = if (low) Danger else Blue)
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, Line.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun ProductRow(product: Product, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        ProductPhoto(product.photoPath, 60.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                product.name,
                style = MaterialTheme.typography.titleMedium,
                color = Navy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = when {
                product.isLow && product.quantity == 0 -> "Sin stock"
                product.isLow -> "Stock bajo"
                product.category.isNotBlank() -> product.category
                else -> null
            }
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (product.isLow) Danger else Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        StockBadge(product)
    }
}

@Composable
fun MovementRow(movement: Movement, showProduct: Boolean = true) {
    val incoming = movement.incoming
    AppCard {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    when (incoming) {
                        true -> Lime
                        false -> DangerSoft
                        null -> Line.copy(alpha = 0.5f)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when (incoming) {
                    true -> "+${movement.quantity}"
                    false -> "−${movement.quantity}"
                    null -> "•"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = when (incoming) {
                    true -> Teal
                    false -> Danger
                    null -> Muted
                },
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (showProduct) movement.sentence() else movement.sentence().substringBefore(" de ${movement.productName}"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Navy,
            )
            if (movement.note.isNotBlank()) {
                Text(movement.note, style = MaterialTheme.typography.bodySmall, color = Blue)
            }
            Text(formatDateTime(movement.createdAt), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Teal, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Navy, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Teal,
        letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
    )
}
