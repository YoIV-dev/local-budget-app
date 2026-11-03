package com.localbudget.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "ホーム", Icons.Default.Home)
    object Expenses : Screen("expenses", "支出一覧", Icons.Default.ReceiptLong)
    object Fridge : Screen("fridge", "冷蔵庫", Icons.Default.Kitchen)
    object FreezerPantry : Screen("freezer_pantry", "冷凍・常温", Icons.Default.AcUnit)
    object Categories : Screen("categories", "カテゴリ", Icons.Default.Category)
    object Settings : Screen("settings", "設定・管理", Icons.Default.Settings)
    object CameraScan : Screen("camera_scan", "レシートスキャン", Icons.Default.ReceiptLong)
    object ReceiptGallery : Screen("receipt_gallery", "レシート画像", Icons.Default.ReceiptLong)
    object RecurringExpenses : Screen("recurring_expenses", "固定支出・サブスク", Icons.Default.Repeat)

    companion object {
        // Lazy: the companion is initialized while the first object touched (e.g. Fridge from a
        // notification intent) is still being constructed, so an eager list would hold null for it
        val bottomNavItems by lazy { listOf(Home, Expenses, Fridge, Settings) }
    }
}
