package com.ferrestock.app.data

import java.text.Normalizer
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String,
    val name: String,
    val description: String = "",
    val category: String = "",
    val unit: String = "u",
    val quantity: Int = 0,
    @SerialName("min_stock") val minStock: Int = 0,
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("created_by_name") val createdByName: String = "",
) {
    val isLow: Boolean get() = quantity <= minStock
}

@Serializable
data class Movement(
    val id: String,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name") val productName: String = "",
    val unit: String = "u",
    val type: String = "",
    val quantity: Int = 0,
    @SerialName("stock_after") val stockAfter: Int? = null,
    val note: String = "",
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("user_name") val userName: String = "",
    @SerialName("created_at") val createdAt: String = "",
) {
    /** true = entra stock, false = sale stock, null = no cambia la cantidad. */
    val incoming: Boolean?
        get() = when (type) {
            "alta", "ingreso" -> true
            "retiro", "orden", "baja" -> false
            else -> null
        }

    /** "Juan retiró 4 u de Plafón de luz" */
    fun sentence(): String {
        val who = userName.ifBlank { "Alguien" }
        return when (type) {
            "alta" -> "$who agregó $productName ($quantity $unit)"
            "ingreso" -> "$who ingresó $quantity $unit de $productName"
            "retiro", "orden" -> "$who retiró $quantity $unit de $productName"
            "edicion" -> "$who editó $productName"
            "baja" -> "$who eliminó $productName"
            else -> "$who modificó $productName"
        }
    }
}

@Serializable
data class OrderItem(
    val id: String = "",
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name") val productName: String = "",
    val unit: String = "u",
    val quantity: Int = 0,
)

@Serializable
data class StockOrder(
    val id: String,
    val number: Long = 0,
    val description: String = "",
    @SerialName("user_name") val userName: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("order_items") val items: List<OrderItem> = emptyList(),
) {
    val code: String get() = "ORD-%06d".format(number)
    val totalUnits: Int get() = items.sumOf { it.quantity }
}

private val esAR = Locale.forLanguageTag("es-AR")
private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", esAR)
private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", esAR)

private fun parseLocal(iso: String) = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
}.getOrNull()

fun formatDateTime(iso: String): String = parseLocal(iso)?.format(dateTimeFmt) ?: iso
fun formatDate(iso: String): String = parseLocal(iso)?.format(dateFmt) ?: iso

private val marks = "\\p{Mn}+".toRegex()

/** Minúsculas y sin tildes, para que "plafon" encuentre "Plafón". */
fun String.searchKey(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(marks, "").lowercase()

fun List<Product>.search(query: String): List<Product> {
    val tokens = query.searchKey().split(' ').filter { it.isNotBlank() }
    if (tokens.isEmpty()) return this
    return filter { p ->
        val haystack = "${p.name} ${p.category} ${p.description}".searchKey()
        tokens.all { it in haystack }
    }
}
