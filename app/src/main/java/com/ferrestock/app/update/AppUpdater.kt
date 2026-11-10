package com.ferrestock.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ferrestock.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Autoactualización desde los GitHub Releases del repo. Cada push a main lo compila CI
 * (.github/workflows/release.yml) y lo publica como release `build-<versionCode>` con el APK.
 */
class AppUpdater(private val context: Context) {

    data class Release(val versionCode: Int, val name: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

    /** La última release, si es más nueva que la versión instalada. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val json = get("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest") ?: return@withContext null
        val release = JSONObject(json)
        val code = release.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return@withContext null
        if (code <= BuildConfig.VERSION_CODE) return@withContext null
        val assets = release.optJSONArray("assets") ?: return@withContext null
        val apk = (0 until assets.length()).map(assets::getJSONObject)
            .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
        Release(
            versionCode = code,
            name = release.optString("name"),
            notes = release.optString("body"),
            apkUrl = apk.getString("browser_download_url"),
            sizeBytes = apk.optLong("size"),
        )
    }

    /** Una versión nueva que GitHub está compilando ahora mismo, si hay. */
    suspend fun building(): PendingBuild? = withContext(Dispatchers.IO) {
        val json = get("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/actions/workflows/$WORKFLOW/runs?per_page=10")
            ?: return@withContext null
        val runs = JSONObject(json).optJSONArray("workflow_runs") ?: return@withContext null
        val all = (0 until runs.length()).map(runs::getJSONObject)
        val active = all.firstOrNull { it.optString("status") != "completed" } ?: return@withContext null
        // Si compila el mismo commit que ya está instalado, no es una versión nueva.
        if (active.optString("head_sha").startsWith(BuildConfig.GIT_SHA) && BuildConfig.GIT_SHA.isNotEmpty()) return@withContext null
        val took = all.filter { it.optString("conclusion") == "success" }
            .mapNotNull { r -> time(r.optString("run_started_at"))?.let { start -> time(r.optString("updated_at"))?.minus(start) } }
            .filter { it > 0 }
        PendingBuild(
            startedAt = time(active.optString("run_started_at")) ?: System.currentTimeMillis(),
            expectedMs = if (took.isEmpty()) 5 * 60_000L else took.average().toLong(),
        )
    }

    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val target = File(dir, "ferrestock-${release.versionCode}.apk")
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        try {
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        target
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Abre el ajuste "Instalar apps desconocidas" de Ferrestock. */
    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Le pasa el APK al instalador del sistema; el usuario confirma ahí. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun time(iso: String?): Long? =
        iso?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            if (connection.responseCode == 200) connection.inputStream.bufferedReader().readText() else null
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val WORKFLOW = "release.yml"
    }
}

/** Una versión que GitHub está compilando: arrancó en [startedAt] y suele tardar [expectedMs]. */
data class PendingBuild(val startedAt: Long, val expectedMs: Long) {
    /** Cuánto debería ir; nunca llega al 100% hasta que sale la release. */
    fun progress(now: Long = System.currentTimeMillis()): Float =
        ((now - startedAt).toFloat() / expectedMs).coerceIn(0f, 0.99f)
}
