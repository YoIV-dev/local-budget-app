package com.localbudget.app.data.reminder

import android.content.Context
import com.localbudget.app.domain.food.FridgeReminderPlanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime

/** On/off switch for the daily fridge reminder (off by default), and what it last notified. */
class FridgeReminderSettings(context: Context) {

    private val prefs = context.getSharedPreferences("fridge_reminder", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _time = MutableStateFlow(LocalTime.ofSecondOfDay(prefs.getInt(KEY_MINUTE_OF_DAY, 18 * 60).coerceIn(0, 1439) * 60L))
    val time: StateFlow<LocalTime> = _time.asStateFlow()

    fun setTime(time: LocalTime) {
        val minute = time.hour * 60 + time.minute
        prefs.edit().putInt(KEY_MINUTE_OF_DAY, minute).apply()
        _time.value = LocalTime.of(time.hour, time.minute)
    }

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    var plannerState: FridgeReminderPlanner.State
        get() = FridgeReminderPlanner.State(
            notifiedKeys = prefs.getStringSet(KEY_NOTIFIED, null).orEmpty().toSet(),
            lastStockCheckOn = prefs.getLong(KEY_STOCK_CHECK_ON, NONE).takeIf { it != NONE }?.let(LocalDate::ofEpochDay)
        )
        set(value) {
            prefs.edit()
                .putStringSet(KEY_NOTIFIED, value.notifiedKeys)
                .putLong(KEY_STOCK_CHECK_ON, value.lastStockCheckOn?.toEpochDay() ?: NONE)
                .apply()
        }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_MINUTE_OF_DAY = "minute_of_day"
        const val KEY_NOTIFIED = "notified_keys"
        const val KEY_STOCK_CHECK_ON = "last_stock_check_on"
        const val NONE = Long.MIN_VALUE
    }
}
