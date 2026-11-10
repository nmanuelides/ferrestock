package com.ferrestock.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ferrestock.app.data.Movement
import com.ferrestock.app.data.Product
import com.ferrestock.app.data.Repo
import com.ferrestock.app.data.StockOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {

    var products by mutableStateOf<List<Product>>(emptyList())
        private set
    var movements by mutableStateOf<List<Movement>>(emptyList())
        private set
    var orders by mutableStateOf<List<StockOrder>>(emptyList())
        private set

    var loaded by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Mensajes de error para mostrar en un snackbar. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Orden en armado: productId -> cantidad. Vive acá para sobrevivir rotaciones. */
    val cart = mutableStateMapOf<String, Int>()

    /** Foto recién sacada/elegida en el formulario de producto, ya comprimida. */
    var pendingPhoto by mutableStateOf<ByteArray?>(null)

    val userName: String get() = Repo.userName
    val userEmail: String get() = Repo.userEmail

    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.tryEmit(friendly(e))
            } finally {
                refreshing = false
                loaded = true
            }
        }
    }

    private suspend fun reload() = coroutineScope {
        val p = async { Repo.products() }
        val m = async { Repo.movements() }
        val o = async { Repo.orders() }
        products = p.await()
        movements = m.await()
        orders = o.await()
    }

    /** Ejecuta una escritura, recarga los datos y avisa. Los errores van al snackbar. */
    fun <T> action(block: suspend () -> T, onSuccess: (T) -> Unit = {}) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                val result = block()
                runCatching { reload() }
                onSuccess(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.tryEmit(friendly(e))
            } finally {
                busy = false
            }
        }
    }

    fun clearSession() {
        products = emptyList()
        movements = emptyList()
        orders = emptyList()
        cart.clear()
        pendingPhoto = null
        loaded = false
    }

    companion object {
        fun friendly(e: Throwable): String {
            val raw = e.message.orEmpty()
            return when {
                "Unable to resolve host" in raw || "timeout" in raw.lowercase() || "Failed to connect" in raw ->
                    "Sin conexión. Revisá internet y probá de nuevo."
                raw.isBlank() -> "Algo salió mal. Probá de nuevo."
                else -> raw.lineSequence().first().take(180)
            }
        }
    }
}
