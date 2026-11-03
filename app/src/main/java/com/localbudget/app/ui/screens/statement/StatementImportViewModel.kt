package com.localbudget.app.ui.screens.statement

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localbudget.app.data.statement.CardStatementImporter
import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.repository.CategoryRepository
import com.localbudget.app.domain.statement.RefundTarget
import com.localbudget.app.domain.statement.StatementCandidate
import com.localbudget.app.domain.statement.StatementPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface StatementImportState {
    data object Idle : StatementImportState
    data object Reading : StatementImportState
    data class Review(val plan: StatementPlan) : StatementImportState
}

/** Imports a credit-card statement CSV: read, review in [StatementImportDialog], then save the ticked rows. */
class StatementImportViewModel(
    private val importer: CardStatementImporter,
    categoryRepository: CategoryRepository
) : ViewModel() {

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow<StatementImportState>(StatementImportState.Idle)
    val state: StateFlow<StatementImportState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Short notices for a toast: nothing found, the file unreadable, how many were imported. */
    val messages: SharedFlow<String> = _messages

    fun read(uris: List<Uri>) {
        _state.value = StatementImportState.Reading
        viewModelScope.launch {
            try {
                val plan = importer.read(uris)
                if (plan.candidates.isEmpty() && plan.refundTargets.isEmpty()) {
                    _messages.tryEmit("明細の行が見つかりませんでした。カード会社のサイトからダウンロードしたCSVを選んでください")
                    _state.value = StatementImportState.Idle
                } else {
                    _state.value = StatementImportState.Review(plan)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("StatementImport", "Could not read the statement", e)
                _messages.tryEmit("ファイルを読み込めませんでした")
                _state.value = StatementImportState.Idle
            }
        }
    }

    fun dismiss() {
        _state.value = StatementImportState.Idle
    }

    fun import(candidates: List<StatementCandidate>, refunds: List<RefundTarget>) {
        viewModelScope.launch {
            importer.save(candidates, refunds)
            _state.value = StatementImportState.Idle
            val removed = if (refunds.isEmpty()) "" else "、返品の${refunds.size}件を削除"
            _messages.tryEmit("カード明細から${candidates.size}件を取り込みました$removed")
        }
    }

    class Factory(
        private val importer: CardStatementImporter,
        private val categoryRepository: CategoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatementImportViewModel(importer, categoryRepository) as T
        }
    }
}
