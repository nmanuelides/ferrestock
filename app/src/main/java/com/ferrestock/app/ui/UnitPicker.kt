package com.ferrestock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ferrestock.app.ui.theme.*

/** Unidades que se ofrecen siempre, con su nombre para mostrar. */
private val DefaultUnits = linkedMapOf(
    "u" to "unidad",
    "m" to "metro",
    "cm" to "centímetro",
    "kg" to "kilo",
    "g" to "gramo",
    "l" to "litro",
    "caja" to "caja",
    "rollo" to "rollo",
    "par" to "par",
    "paquete" to "paquete",
)

/** Las unidades de siempre más las que ya usan los productos (y la elegida), sin repetir. */
fun unitOptions(inUse: Collection<String>, current: String): List<String> =
    (DefaultUnits.keys + inUse.map { it.trim() } + current.trim())
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

/** Campo con el aspecto de los demás, pero que abre el selector en vez del teclado. */
@Composable
fun UnitField(value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text("Unidad") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, tint = Teal) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Line,
                unfocusedBorderColor = Line,
                focusedLabelColor = Muted,
                unfocusedLabelColor = Muted,
            ),
        )
        // El campo es solo lectura: una capa encima toma el toque y abre el selector.
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
        )
    }
}

/** Modal para elegir la unidad o crear una nueva. */
@Composable
fun UnitPickerDialog(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var newUnit by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    fun create() {
        val value = newUnit.trim()
        if (value.isNotEmpty()) onSelect(options.firstOrNull { it.equals(value, ignoreCase = true) } ?: value)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 24.dp,
        ) {
            Column(Modifier.padding(top = 22.dp, bottom = 16.dp)) {
                Text(
                    "Unidad",
                    Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = Navy,
                )
                Text(
                    "Usá siempre la misma para que el stock se lea igual en todos los productos.",
                    Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )

                LazyColumn(
                    Modifier.heightIn(max = 340.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(options, key = { it.lowercase() }) { option ->
                        val isSelected = option.equals(selected, ignoreCase = true)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Lime else Color.Transparent)
                                .clickable { onSelect(option) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                option,
                                style = MaterialTheme.typography.titleMedium,
                                color = Navy,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            )
                            DefaultUnits[option]?.takeIf { it != option }?.let {
                                Spacer(Modifier.width(10.dp))
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted)
                            }
                            Spacer(Modifier.weight(1f))
                            if (isSelected) Icon(Icons.Filled.Check, null, tint = Teal)
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Line)

                if (!creating) {
                    Row(
                        Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { creating = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, null, tint = Teal)
                        Spacer(Modifier.width(10.dp))
                        Text("Crear unidad nueva", style = MaterialTheme.typography.titleMedium, color = Teal)
                    }
                } else {
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newUnit,
                            onValueChange = { newUnit = it.take(12) },
                            label = { Text("Nueva unidad") },
                            placeholder = { Text("Ej.: bolsa, tubo, m²", color = Muted) },
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { create() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Teal,
                                unfocusedBorderColor = Line,
                                focusedLabelColor = Teal,
                                cursorColor = Teal,
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SecondaryButton("Cancelar", { creating = false; newUnit = "" }, Modifier.weight(1f))
                            PrimaryButton("Agregar", ::create, Modifier.weight(1f), enabled = newUnit.isNotBlank())
                        }
                    }
                }
            }
        }
    }
}
