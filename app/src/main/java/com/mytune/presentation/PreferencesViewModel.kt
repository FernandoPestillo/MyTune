package com.mytune.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytune.data.preferences.PreferencesRepository
import com.mytune.domain.model.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PreferencesUiState(val preferences: AppPreferences = AppPreferences(), val loaded: Boolean = false,
    val saving: Boolean = false, val error: String? = null)

class PreferencesViewModel(private val repository: PreferencesRepository) : ViewModel() {
    private val mutable = MutableStateFlow(PreferencesUiState())
    val state = mutable.asStateFlow()
    private val writes = Mutex()
    private var observationFailed = false
    init { viewModelScope.launch {
        try { repository.preferences.collect { value -> mutable.update { it.copy(preferences = value, loaded = true) } } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IOException) { observationFailed = true; mutable.update { it.copy(loaded = true, error = "Não foi possível ler as preferências. Tente alterar a opção novamente.") } }
    } }
    fun headstock(value: HeadstockType) = update { repository.headstock(value) }
    fun theme(value: ThemeMode) = update { repository.theme(value) }
    fun haptics(value: Boolean) = update { repository.haptics(value) }
    fun stringAnimation(value: Boolean) = update { repository.stringAnimation(value) }
    private fun update(write: suspend () -> AppPreferences) { viewModelScope.launch {
        writes.withLock {
            mutable.update { it.copy(saving = true, error = null) }
            try {
                val committed = write()
                // After a failed read, that collector has ended; a successful transaction can recover the UI.
                if (observationFailed) mutable.update { it.copy(preferences = committed, loaded = true) }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IOException) { mutable.update { it.copy(error = "Não foi possível salvar a preferência. Tente novamente.") } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    } }
}
