package com.localbudget.app.data.statement

import android.content.Context
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.text.JapaneseTextNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Explicitly selected card statement merchant and amount pairs. */
class SubscriptionSelections(context: Context) {
    private val prefs = context.getSharedPreferences("subscription_selections", Context.MODE_PRIVATE)
    private val selected = MutableStateFlow(prefs.getStringSet("keys", emptySet())?.toSet() ?: emptySet())
    val keys = selected.asStateFlow()

    fun set(keys: Set<String>) {
        prefs.edit().putStringSet("keys", keys.toSet()).apply()
        selected.value = keys.toSet()
    }

    companion object {
        fun key(expense: Expense): String = "${expense.amount}:${JapaneseTextNormalizer.matchKey(expense.storeName)}"
    }
}
