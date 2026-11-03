package com.localbudget.app.ui.navigation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.YearMonth

class SelectedMonthViewModel : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())
}
