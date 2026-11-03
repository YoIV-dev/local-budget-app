package com.localbudget.app.data.ocr

import android.content.Context
import com.localbudget.app.data.local.dao.ExpenseDao
import com.localbudget.app.data.local.dao.FridgeDao
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.model.SourceType
import com.localbudget.app.domain.ocr.ReceiptItemParser
import com.localbudget.app.domain.ocr.ReceiptParser

/**
 * Brings what earlier scans saved up to how receipts are read now, once per [VERSION]: store names
 * of scanned expenses ("●4maruetsu" → "マルエツ"), their receipt lines (misread names corrected,
 * payment rows and notes dropped) and the fridge items taken from them (names only).
 * Expenses entered by hand are left alone, and so is anything already written the current way.
 */
class SavedScanTidier(
    context: Context,
    private val expenseDao: ExpenseDao,
    private val fridgeDao: FridgeDao
) {
    private val prefs = context.getSharedPreferences("saved_scan_tidier", Context.MODE_PRIVATE)

    suspend fun runOnce() {
        if (prefs.getInt(KEY_VERSION, 0) >= VERSION) return
        tidy()
        prefs.edit().putInt(KEY_VERSION, VERSION).apply()
    }

    private suspend fun tidy() {
        val now = System.currentTimeMillis()
        for (expense in expenseDao.getExpensesBySource(SourceType.RECEIPT_OCR.name)) {
            val store = ReceiptParser.tidyStoreName(expense.storeName)
            if (store != expense.storeName) expenseDao.updateStoreName(expense.id, store, now)

            val lines = expenseDao.getReceiptItems(expense.id)
            val tidied = ReceiptItemParser.tidySaved(lines.map { it.toDomain() })
            if (tidied != lines.map { it.toDomain() }) {
                val kept = lines.zip(tidied).mapNotNull { (line, item) -> item?.let { line.copy(name = it.name) } }
                expenseDao.replaceReceiptItems(expense.id, kept)
            }
        }

        // Fridge items are the user's own list: names are corrected, nothing is removed
        for (group in fridgeDao.getItemsFromReceipts().groupBy { it.expenseId }.values) {
            val tidied = ReceiptItemParser.tidySaved(group.map { ReceiptLineItem(it.name) })
            group.zip(tidied).forEach { (item, line) ->
                val name = line?.name ?: return@forEach
                if (name != item.name) fridgeDao.updateName(item.id, name)
            }
        }
    }

    private companion object {
        const val KEY_VERSION = "version"
        // Raise when reading improves again, to bring saved scans up to it once more
        const val VERSION = 1
    }
}
