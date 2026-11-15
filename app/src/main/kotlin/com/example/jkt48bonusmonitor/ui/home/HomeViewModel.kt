package com.example.jkt48bonusmonitor.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jkt48bonusmonitor.data.api.BonusApiClient
import com.example.jkt48bonusmonitor.data.datastore.AppPreferences
import com.example.jkt48bonusmonitor.data.history.HistoryEntry
import com.example.jkt48bonusmonitor.data.history.HistoryRepository
import com.example.jkt48bonusmonitor.data.repository.BonusMonitorRepository
import com.example.jkt48bonusmonitor.domain.model.AppState
import com.example.jkt48bonusmonitor.domain.model.MonitoringStatus
import com.example.jkt48bonusmonitor.notification.NotificationHelper
import com.example.jkt48bonusmonitor.worker.BonusMonitorWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val appPreferences = AppPreferences(context)
    private val apiClient = BonusApiClient()
    private val historyRepo = HistoryRepository(context)
    private val notifHelper = NotificationHelper(context)

    private val repository = BonusMonitorRepository(
        appPreferences, apiClient, historyRepo, notifHelper
    )

    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _exclusiveIdInput = MutableStateFlow("EX79CB")
    val exclusiveIdInput: StateFlow<String> = _exclusiveIdInput.asStateFlow()

    private val _exclusiveIdError = MutableStateFlow<String?>(null)
    val exclusiveIdError: StateFlow<String?> = _exclusiveIdError.asStateFlow()

    init {
        notifHelper.createNotificationChannel()
        observePreferences()
        loadHistory()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                appPreferences.exclusiveIdFlow,
                appPreferences.monitoringEnabledFlow,
                appPreferences.lastCheckTimestampFlow
            ) { exclusiveId, monitoringEnabled, lastCheck ->
                Triple(exclusiveId, monitoringEnabled, lastCheck)
            }.collect { (exclusiveId, monitoringEnabled, lastCheck) ->
                _uiState.update { current ->
                    current.copy(
                        exclusiveId = exclusiveId,
                        monitoringStatus = if (monitoringEnabled) MonitoringStatus.ACTIVE else MonitoringStatus.STOPPED,
                        lastCheckTimestamp = lastCheck
                    )
                }
                _exclusiveIdInput.value = exclusiveId
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _history.value = historyRepo.readHistory()
        }
    }

    fun onExclusiveIdChange(value: String) {
        _exclusiveIdInput.value = value
        if (value.isNotBlank()) _exclusiveIdError.value = null
    }

    fun startMonitoring() {
        val id = _exclusiveIdInput.value.trim()
        if (id.isBlank()) { _exclusiveIdError.value = "Exclusive ID cannot be empty"; return }
        _exclusiveIdError.value = null
        viewModelScope.launch {
            appPreferences.setExclusiveId(id)
            appPreferences.setMonitoringEnabled(true)
            BonusMonitorWorker.schedule(context)
            _uiState.update { it.copy(lastRequestStatus = "Monitoring started") }
        }
    }

    fun stopMonitoring() {
        viewModelScope.launch {
            appPreferences.setMonitoringEnabled(false)
            BonusMonitorWorker.cancel(context)
            _uiState.update { it.copy(lastRequestStatus = "Monitoring stopped") }
        }
    }

    fun checkNow() {
        val id = _exclusiveIdInput.value.trim()
        if (id.isBlank()) { _exclusiveIdError.value = "Exclusive ID cannot be empty"; return }
        _exclusiveIdError.value = null
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, lastRequestStatus = "Checking…") }
            appPreferences.setExclusiveId(id)
            val result = repository.performCheck()
            _uiState.update { it.copy(
                isLoading = false,
                lastRequestStatus = result.statusMessage,
                slots = result.slots
            )}
            loadHistory()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepo.clearHistory()
            _history.value = emptyList()
        }
    }
}
