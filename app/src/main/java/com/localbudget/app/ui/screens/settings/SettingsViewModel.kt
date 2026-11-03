package com.localbudget.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localbudget.app.data.backup.CsvExportManager
import com.localbudget.app.data.backup.JsonBackupManager
import com.localbudget.app.data.reminder.FridgeReminderScheduler
import com.localbudget.app.data.reminder.FridgeReminderSettings
import com.localbudget.app.data.scanlog.ScanLogSettings
import com.localbudget.app.data.scanlog.ScanLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalTime

class SettingsViewModel(
    private val jsonBackupManager: JsonBackupManager,
    private val csvExportManager: CsvExportManager,
    private val scanLogSettings: ScanLogSettings,
    private val scanLogStore: ScanLogStore,
    private val fridgeReminderSettings: FridgeReminderSettings,
    private val fridgeReminderScheduler: FridgeReminderScheduler
) : ViewModel() {

    val fridgeReminderEnabled: StateFlow<Boolean> = fridgeReminderSettings.enabled
    val fridgeReminderTime: StateFlow<LocalTime> = fridgeReminderSettings.time

    val scanLogEnabled: StateFlow<Boolean> = scanLogSettings.enabled

    private val _scanLogCount = MutableStateFlow(0)
    val scanLogCount: StateFlow<Int> = _scanLogCount.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun exportJson(outputStream: OutputStream) {
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                jsonBackupManager.exportToJson(outputStream)
                _statusMessage.value = "JSONバックアップを正常に保存しました"
            } catch (e: Exception) {
                _statusMessage.value = "エクスポートに失敗しました: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun exportCsv(outputStream: OutputStream) {
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                csvExportManager.exportToCsv(outputStream)
                _statusMessage.value = "CSVファイルを正常に出力しました"
            } catch (e: Exception) {
                _statusMessage.value = "CSV出力に失敗しました: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun importJson(inputStream: InputStream) {
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val result = jsonBackupManager.importFromJson(inputStream)
                _statusMessage.value = result.message
            } catch (e: Exception) {
                _statusMessage.value = "インポートに失敗しました: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun setScanLogEnabled(enabled: Boolean) {
        scanLogSettings.setEnabled(enabled)
    }

    /** Call with true only once notification permission is granted (Android 13+). */
    fun setFridgeReminderEnabled(enabled: Boolean) {
        fridgeReminderSettings.setEnabled(enabled)
        fridgeReminderScheduler.apply(enabled)
    }

    fun setFridgeReminderTime(time: LocalTime) {
        fridgeReminderSettings.setTime(time)
        fridgeReminderScheduler.reschedule()
    }

    fun showMessage(message: String) {
        _statusMessage.value = message
    }

    fun refreshScanLogCount() {
        viewModelScope.launch {
            _scanLogCount.value = withContext(Dispatchers.IO) { scanLogStore.count() }
        }
    }

    /** [openStream] is called on a background thread; the stream is closed when the zip is done. */
    fun exportScanLog(openStream: () -> OutputStream?) {
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = openStream() ?: error("出力先を開けませんでした")
                    stream.use { scanLogStore.exportZip(it) }
                }
                _statusMessage.value = "検証ログを書き出しました"
            } catch (e: Exception) {
                _statusMessage.value = "検証ログの書き出しに失敗しました: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun clearScanLog() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { scanLogStore.clear() }
            _scanLogCount.value = 0
            _statusMessage.value = "検証ログを削除しました"
        }
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    class Factory(
        private val jsonBackupManager: JsonBackupManager,
        private val csvExportManager: CsvExportManager,
        private val scanLogSettings: ScanLogSettings,
        private val scanLogStore: ScanLogStore,
        private val fridgeReminderSettings: FridgeReminderSettings,
        private val fridgeReminderScheduler: FridgeReminderScheduler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                jsonBackupManager, csvExportManager, scanLogSettings, scanLogStore,
                fridgeReminderSettings, fridgeReminderScheduler
            ) as T
        }
    }
}
