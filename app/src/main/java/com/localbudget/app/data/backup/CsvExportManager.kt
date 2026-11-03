package com.localbudget.app.data.backup

import com.localbudget.app.domain.repository.CategoryRepository
import com.localbudget.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStream

class CsvExportManager(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository
) {

    suspend fun exportToCsv(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val expenses = expenseRepository.getAllExpenses().first()
        val categories = categoryRepository.getAllCategories().first().associateBy { it.id }

        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            // Write UTF-8 BOM for Microsoft Excel compatibility
            writer.write("\uFEFF")

            // Header
            writer.write("日付,店舗名,カテゴリ,金額,メモ,入力元\n")

            // Rows
            for (e in expenses) {
                val catName = e.category?.name ?: categories[e.categoryId]?.name ?: "未設定"
                val row = listOf(
                    e.date.toString(),
                    escapeCsv(e.storeName),
                    escapeCsv(catName),
                    e.amount.toString(),
                    escapeCsv(e.memo),
                    escapeCsv(e.sourceType.displayName)
                ).joinToString(",")

                writer.write(row)
                writer.write("\n")
            }
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
