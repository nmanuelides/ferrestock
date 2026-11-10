package com.ferrestock.app.data

import android.content.Context
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Modo demo: se usa mientras local.properties no tenga las claves de Supabase.
 * Imita las funciones SQL con datos de ejemplo guardados en el teléfono.
 */
object Demo {
    const val USER = "Usuario Demo"

    @Serializable
    private data class State(
        val products: List<Product> = emptyList(),
        val movements: List<Movement> = emptyList(),
        val orders: List<StockOrder> = emptyList(),
        val nextOrder: Long = 1,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var dir: File
    private var state = State()

    private val file get() = File(dir, "demo.json")

    @Synchronized
    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = context.filesDir
        state = runCatching { json.decodeFromString<State>(file.readText()) }.getOrElse { seed() }
        save()
    }

    @Synchronized
    fun reset() {
        File(dir, "demo_photos").deleteRecursively()
        state = seed()
        save()
    }

    private fun save() {
        runCatching { file.writeText(json.encodeToString(State.serializer(), state)) }
    }

    private fun now() = OffsetDateTime.now().toString()
    private fun id() = UUID.randomUUID().toString()

    // ---------------- Lectura ----------------

    @Synchronized fun products() = state.products.sortedBy { it.name.lowercase() }
    @Synchronized fun movements() = state.movements
    @Synchronized fun productMovements(productId: String) = state.movements.filter { it.productId == productId }
    @Synchronized fun orders() = state.orders

    // ---------------- Escritura ----------------

    private fun log(p: Product, type: String, qty: Int, stockAfter: Int, note: String, orderId: String? = null) =
        Movement(id(), p.id, p.name, p.unit, type, qty, stockAfter, note, orderId, USER, now())

    private fun find(productId: String) =
        state.products.firstOrNull { it.id == productId } ?: throw IllegalStateException("Producto no encontrado")

    private fun replace(p: Product) = state.products.map { if (it.id == p.id) p else it }

    fun savePhoto(productId: String, bytes: ByteArray): String {
        val photos = File(dir, "demo_photos").apply { mkdirs() }
        val f = File(photos, "${productId}_${System.currentTimeMillis()}.jpg")
        f.writeBytes(bytes)
        return f.absolutePath
    }

    @Synchronized
    fun createProduct(id: String, name: String, description: String, category: String, unit: String, quantity: Int, minStock: Int, photoPath: String?) {
        val p = Product(id, name, description, category, unit, quantity, minStock, photoPath, now(), USER)
        state = state.copy(
            products = state.products + p,
            movements = listOf(log(p, "alta", quantity, quantity, "Producto agregado")) + state.movements,
        )
        save()
    }

    @Synchronized
    fun updateProduct(id: String, name: String, description: String, category: String, unit: String, minStock: Int, photoPath: String?) {
        val p = find(id).copy(name = name, description = description, category = category, unit = unit, minStock = minStock, photoPath = photoPath)
        state = state.copy(
            products = replace(p),
            movements = listOf(log(p, "edicion", 0, p.quantity, "Datos del producto editados")) + state.movements,
        )
        save()
    }

    @Synchronized
    fun adjustStock(productId: String, delta: Int, note: String) {
        val p = find(productId)
        if (delta == 0) throw IllegalStateException("La cantidad tiene que ser mayor a cero")
        val after = p.quantity + delta
        if (after < 0) throw IllegalStateException("Stock insuficiente de ${p.name}: quedan ${p.quantity} ${p.unit}")
        state = state.copy(
            products = replace(p.copy(quantity = after)),
            movements = listOf(log(p, if (delta > 0) "ingreso" else "retiro", kotlin.math.abs(delta), after, note)) + state.movements,
        )
        save()
    }

    @Synchronized
    fun deleteProduct(productId: String) {
        val p = find(productId)
        state = state.copy(
            products = state.products.filter { it.id != productId },
            movements = listOf(log(p, "baja", p.quantity, 0, "Producto eliminado")) + state.movements,
        )
        save()
    }

    @Synchronized
    fun deleteOrder(orderId: String) {
        val order = state.orders.firstOrNull { it.id == orderId }
            ?: throw IllegalStateException("La orden no existe o ya fue eliminada")
        val note = listOf("${order.code} eliminada", order.description).filter { it.isNotBlank() }.joinToString(" · ")

        // Igual que en Supabase: a un producto ya eliminado no se le devuelve stock.
        var products = state.products
        val moves = order.items.mapNotNull { item ->
            val p = products.firstOrNull { it.id == item.productId } ?: return@mapNotNull null
            val after = p.quantity + item.quantity
            products = products.map { if (it.id == p.id) it.copy(quantity = after) else it }
            log(p, "devolucion", item.quantity, after, note, orderId)
        }
        state = state.copy(
            products = products,
            movements = moves + state.movements,
            orders = state.orders.filter { it.id != orderId },
        )
        save()
    }

    @Synchronized
    fun createOrder(description: String, items: Map<String, Int>): String {
        if (items.isEmpty()) throw IllegalStateException("La orden no tiene productos")
        // Todo o nada: primero se valida todo, después se descuenta.
        val picked = items.map { (productId, qty) ->
            val p = find(productId)
            if (qty <= 0) throw IllegalStateException("Cantidad inválida en la orden")
            if (p.quantity < qty) throw IllegalStateException("Stock insuficiente de ${p.name}: quedan ${p.quantity} ${p.unit}")
            p to qty
        }
        val orderId = id()
        val number = state.nextOrder
        val code = "ORD-%06d".format(number)
        val note = listOf(code, description).filter { it.isNotBlank() }.joinToString(" · ")

        var products = state.products
        val moves = mutableListOf<Movement>()
        val orderItems = picked.map { (p, qty) ->
            val after = p.quantity - qty
            products = products.map { if (it.id == p.id) it.copy(quantity = after) else it }
            moves += log(p, "orden", qty, after, note, orderId)
            OrderItem(id(), p.id, p.name, p.unit, qty)
        }
        state = state.copy(
            products = products,
            movements = moves + state.movements,
            orders = listOf(StockOrder(orderId, number, description, USER, now(), orderItems)) + state.orders,
            nextOrder = number + 1,
        )
        save()
        return orderId
    }

    // ---------------- Datos de ejemplo ----------------

    private fun seed(): State {
        fun ago(days: Long, hours: Long = 0) = OffsetDateTime.now().minusDays(days).minusHours(hours).toString()
        fun product(name: String, category: String, unit: String, qty: Int, min: Int, description: String, days: Long, by: String) =
            Product(id(), name, description, category, unit, qty, min, null, ago(days), by)

        val plafon = product("Plafón de luz LED 18W", "Iluminación", "u", 24, 6, "Redondo, luz fría, para embutir.", 21, "Juan Pérez")
        val cable = product("Cable unipolar 2,5 mm", "Electricidad", "m", 180, 50, "Rollo normalizado, color celeste.", 20, "Juan Pérez")
        val tornillo = product("Tornillo autoperforante 8x1", "Fijaciones", "caja", 12, 4, "Caja x 100 unidades, punta mecha.", 18, "Marta Gómez")
        val cinta = product("Cinta aisladora negra", "Electricidad", "u", 3, 5, "Rollo de 20 m.", 18, "Marta Gómez")
        val llave = product("Llave térmica bipolar 20A", "Electricidad", "u", 9, 3, "Curva C, riel DIN.", 15, "Juan Pérez")
        val canio = product("Caño PVC 110 mm", "Sanitarios", "u", 0, 2, "Tira de 4 m, para desagüe.", 12, "Marta Gómez")
        val pintura = product("Látex interior blanco 20 L", "Pinturería", "lata", 7, 2, "Mate, lavable.", 9, "Juan Pérez")
        val taco = product("Taco Fischer 8 mm", "Fijaciones", "caja", 15, 5, "Caja x 50 unidades.", 6, "Marta Gómez")
        val all = listOf(plafon, cable, tornillo, cinta, llave, canio, pintura, taco)

        fun move(p: Product, type: String, qty: Int, after: Int, note: String, who: String, days: Long, hours: Long = 0, orderId: String? = null) =
            Movement(id(), p.id, p.name, p.unit, type, qty, after, note, orderId, who, ago(days, hours))

        val orderId = id()
        val order = StockOrder(
            orderId, 1, "Materiales para obra Rivadavia 1200", "Marta Gómez", ago(2),
            listOf(
                OrderItem(id(), cable.id, cable.name, cable.unit, 20),
                OrderItem(id(), llave.id, llave.name, llave.unit, 1),
                OrderItem(id(), cinta.id, cinta.name, cinta.unit, 2),
            ),
        )
        val orderNote = "ORD-000001 · ${order.description}"

        val movements = listOf(
            move(plafon, "retiro", 4, 24, "Instalación oficina de Alem 450", "Juan Pérez", 0, 3),
            move(pintura, "ingreso", 4, 7, "Compra a proveedor", "Marta Gómez", 1),
            move(cable, "orden", 20, 180, orderNote, "Marta Gómez", 2, 0, orderId),
            move(llave, "orden", 1, 9, orderNote, "Marta Gómez", 2, 0, orderId),
            move(cinta, "orden", 2, 3, orderNote, "Marta Gómez", 2, 0, orderId),
            move(canio, "retiro", 3, 0, "Reparación de desagüe en depósito", "Juan Pérez", 4),
        ) + all.sortedByDescending { it.createdAt }.map { p ->
            Movement(id(), p.id, p.name, p.unit, "alta", p.quantity, p.quantity, "Producto agregado", null, p.createdByName, p.createdAt)
        }

        return State(all, movements, listOf(order), nextOrder = 2)
    }
}
