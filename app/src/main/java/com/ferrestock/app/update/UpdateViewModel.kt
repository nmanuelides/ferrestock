package com.ferrestock.app.update

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val name: String) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Building(val build: PendingBuild) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Busca, descarga e instala versiones nuevas desde GitHub Releases. */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val updater = AppUpdater(app)
    private var release: AppUpdater.Release? = null
    private var watching: Job? = null

    var state by mutableStateOf<UpdateState>(UpdateState.None)
        private set

    /** Chequeo automático (al abrir o volver a la app): solo releases ya publicadas. */
    fun checkInBackground() {
        if (state is UpdateState.Downloading) return
        viewModelScope.launch { lookUp(builds = false) }
    }

    /** "Buscar actualizaciones": además avisa si hay una versión compilándose. */
    suspend fun checkNow(): UpdateState {
        if (state is UpdateState.Downloading) return state
        return lookUp(builds = true)
    }

    /**
     * Una release publicada o, si se pidió ([builds]), una versión que se está compilando.
     * Mientras compila, vuelve a mirar cada minuto hasta que salga.
     */
    private suspend fun lookUp(builds: Boolean): UpdateState {
        release = runCatching { updater.check() }.getOrNull()
        val found = release?.let { UpdateState.Available(it.name) }
            ?: (if (builds) runCatching { updater.building() }.getOrNull() else null)?.let { UpdateState.Building(it) }
            ?: UpdateState.None
        state = found
        if (found is UpdateState.Building && watching?.isActive != true) {
            watching = viewModelScope.launch {
                while (state is UpdateState.Building) {
                    delay(60_000L)
                    if (state !is UpdateState.Building) break
                    lookUp(builds = true)
                }
            }
        }
        return found
    }

    fun install() {
        val target = release ?: return
        if (!updater.canInstall()) {
            updater.openInstallPermissionSettings()
            return
        }
        viewModelScope.launch {
            runCatching {
                val apk = updater.download(target) { state = UpdateState.Downloading(it) }
                updater.install(apk)
                state = UpdateState.Available(target.name)
            }.onFailure { state = UpdateState.Failed(it.message ?: "error desconocido") }
        }
    }
}
