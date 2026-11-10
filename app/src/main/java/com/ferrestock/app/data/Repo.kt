package com.ferrestock.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.ferrestock.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val PHOTOS = "product-photos"

object Repo {

    val configured: Boolean
        get() = BuildConfig.SUPABASE_URL.startsWith("http") &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank() &&
            BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** Sin claves de Supabase la app funciona con datos de ejemplo locales. */
    val demo: Boolean get() = !configured

    val client: SupabaseClient by lazy {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }

    // ---------------- Sesión ----------------

    val sessionStatus get() = client.auth.sessionStatus

    val userName: String
        get() {
            if (demo) return Demo.USER
            val user = client.auth.currentUserOrNull() ?: return ""
            val meta = user.userMetadata
            return meta?.get("full_name")?.jsonPrimitive?.contentOrNull
                ?: meta?.get("name")?.jsonPrimitive?.contentOrNull
                ?: user.email?.substringBefore('@')
                ?: ""
        }

    val userEmail: String get() = if (demo) "Modo demo · datos de ejemplo" else client.auth.currentUserOrNull()?.email ?: ""

    /** Abre el selector de cuentas de Google; si la cuenta no existía en Supabase, la crea. */
    suspend fun signInWithGoogle(activityContext: Context) {
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(hashedNonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
        val googleToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken

        client.auth.signInWith(IDToken) {
            idToken = googleToken
            provider = Google
            nonce = rawNonce
        }
    }

    suspend fun signOut(context: Context) {
        if (demo) return Demo.reset()
        client.auth.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Aviso para la pantalla de login (por ejemplo, cuenta desactivada). */
    var loginMessage by mutableStateOf<String?>(null)

    /** Si la cuenta está habilitada (perfil activo). */
    suspend fun isMember(): Boolean =
        demo || client.postgrest.rpc("is_member").decodeAs<JsonPrimitive>().boolean

    /** Cierra la sesión de una cuenta desactivada o eliminada y avisa en el login. */
    suspend fun signOutInactive() {
        loginMessage = "Esta cuenta fue eliminada o desactivada. Si fue un error, pedí que la reactiven."
        runCatching { client.auth.signOut(SignOutScope.LOCAL) }
    }

    /**
     * "Eliminar cuenta": la desactiva en Supabase (se conserva para la trazabilidad
     * del historial), cierra todas sus sesiones y la del teléfono.
     */
    suspend fun deleteAccount(context: Context) {
        if (demo) return Demo.reset()
        client.postgrest.rpc("delete_my_account")
        loginMessage = "Tu cuenta fue eliminada."
        // Las sesiones ya se cerraron en el servidor: alcanza con borrar la local.
        runCatching { client.auth.signOut(SignOutScope.LOCAL) }
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    // ---------------- Lectura ----------------

    suspend fun products(): List<Product> =
        if (demo) Demo.products() else client.from("products").select {
            filter { eq("active", true) }
            order("name", Order.ASCENDING)
        }.decodeList()

    suspend fun movements(limit: Long = 200): List<Movement> =
        if (demo) Demo.movements() else client.from("movements").select {
            order("created_at", Order.DESCENDING)
            limit(limit)
        }.decodeList()

    suspend fun productMovements(productId: String): List<Movement> =
        if (demo) Demo.productMovements(productId) else client.from("movements").select {
            filter { eq("product_id", productId) }
            order("created_at", Order.DESCENDING)
            limit(50)
        }.decodeList()

    suspend fun orders(): List<StockOrder> =
        if (demo) Demo.orders() else client.from("orders").select(Columns.raw("*, order_items(*)")) {
            filter { filter("deleted_at", FilterOperator.IS, "null") }
            order("number", Order.DESCENDING)
            limit(200)
        }.decodeList()

    fun photoUrl(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let {
            if (demo) "file://$it" else client.storage.from(PHOTOS).publicUrl(it)
        }

    // ---------------- Escritura (todo vía funciones SQL, que registran el movimiento) ----------------

    suspend fun saveProduct(
        existing: Product?,
        name: String,
        description: String,
        category: String,
        unit: String,
        quantity: Int,
        minStock: Int,
        newPhoto: ByteArray?,
    ) {
        val id = existing?.id ?: UUID.randomUUID().toString()
        if (demo) {
            val path = newPhoto?.let { Demo.savePhoto(id, it) } ?: existing?.photoPath
            if (existing == null) Demo.createProduct(id, name, description, category, unit, quantity, minStock, path)
            else Demo.updateProduct(id, name, description, category, unit, minStock, path)
            return
        }
        val photoPath = if (newPhoto != null) {
            val path = "$id/${System.currentTimeMillis()}.jpg"
            client.storage.from(PHOTOS).upload(path, newPhoto)
            path
        } else {
            existing?.photoPath
        }

        if (existing == null) {
            client.postgrest.rpc("create_product", buildJsonObject {
                put("p_id", id)
                put("p_name", name)
                put("p_description", description)
                put("p_category", category)
                put("p_unit", unit)
                put("p_quantity", quantity)
                put("p_min_stock", minStock)
                put("p_photo_path", photoPath)
            })
        } else {
            client.postgrest.rpc("update_product", buildJsonObject {
                put("p_id", id)
                put("p_name", name)
                put("p_description", description)
                put("p_category", category)
                put("p_unit", unit)
                put("p_min_stock", minStock)
                put("p_photo_path", photoPath)
            })
        }
    }

    /** delta > 0 ingresa, delta < 0 retira. */
    suspend fun adjustStock(productId: String, delta: Int, note: String) {
        if (demo) return Demo.adjustStock(productId, delta, note)
        client.postgrest.rpc("adjust_stock", buildJsonObject {
            put("p_product_id", productId)
            put("p_delta", delta)
            put("p_note", note)
        })
    }

    suspend fun deleteProduct(productId: String) {
        if (demo) return Demo.deleteProduct(productId)
        client.postgrest.rpc("delete_product", buildJsonObject { put("p_id", productId) })
    }

    /** Marca la orden como eliminada y devuelve al stock lo que consumió. */
    suspend fun deleteOrder(orderId: String) {
        if (demo) return Demo.deleteOrder(orderId)
        client.postgrest.rpc("delete_order", buildJsonObject { put("p_order_id", orderId) })
    }

    /** Devuelve el id de la orden creada. */
    suspend fun createOrder(description: String, items: Map<String, Int>): String =
        if (demo) Demo.createOrder(description, items) else client.postgrest.rpc("create_order", buildJsonObject {
            put("p_description", description)
            put("p_items", buildJsonArray {
                items.forEach { (productId, qty) ->
                    add(buildJsonObject {
                        put("product_id", productId)
                        put("quantity", qty)
                    })
                }
            })
        }).decodeAs<String>()
}
