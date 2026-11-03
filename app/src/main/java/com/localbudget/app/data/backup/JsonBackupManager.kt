package com.localbudget.app.data.backup

import com.localbudget.app.domain.model.Budget
import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.FridgeItemStatus
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.model.StorageLocation
import com.localbudget.app.domain.model.RecurringExpense
import com.localbudget.app.domain.model.SourceType
import com.localbudget.app.domain.repository.BudgetRepository
import com.localbudget.app.domain.repository.CategoryRepository
import com.localbudget.app.domain.repository.ExpenseRepository
import com.localbudget.app.domain.repository.FridgeRepository
import com.localbudget.app.domain.repository.RecurringExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

class JsonBackupManager(
    private val categoryRepository: CategoryRepository,
    private val expenseRepository: ExpenseRepository,
    private val recurringRepository: RecurringExpenseRepository,
    private val budgetRepository: BudgetRepository,
    private val fridgeRepository: FridgeRepository? = null
) {

    suspend fun exportToJson(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "local-budget")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        // Categories
        val categories = categoryRepository.getAllCategories().first()
        val catArray = JSONArray()
        for (c in categories) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("iconName", c.iconName)
            obj.put("colorHex", c.colorHex)
            if (c.budgetLimit != null) obj.put("budgetLimit", c.budgetLimit)
            obj.put("isDefault", c.isDefault)
            catArray.put(obj)
        }
        root.put("categories", catArray)

        // Expenses
        val expenses = expenseRepository.getAllExpenses().first()
        val receiptItems = expenseRepository.getAllReceiptItems()
        val expArray = JSONArray()
        for (e in expenses) {
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("date", e.date.toString()) // "YYYY-MM-DD"
            obj.put("amount", e.amount)
            obj.put("storeName", e.storeName)
            obj.put("categoryId", e.categoryId)
            obj.put("memo", e.memo)
            obj.put("sourceType", e.sourceType.name)
            obj.put("hasReceiptImage", e.hasReceiptImage)
            obj.put("createdAt", e.createdAt)
            obj.put("updatedAt", e.updatedAt)
            receiptItems[e.id]?.let { items -> obj.put("items", receiptItemsToJson(items)) }
            expArray.put(obj)
        }
        root.put("expenses", expArray)

        // Recurring Expenses
        val recurring = recurringRepository.getAllRecurringExpenses().first()
        val recArray = JSONArray()
        for (r in recurring) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("name", r.name)
            obj.put("amount", r.amount)
            obj.put("categoryId", r.categoryId)
            obj.put("dayOfMonth", r.dayOfMonth)
            obj.put("isActive", r.isActive)
            obj.put("memo", r.memo)
            obj.put("createdAt", r.createdAt)
            recArray.put(obj)
        }
        root.put("recurringExpenses", recArray)

        // Fridge Items
        fridgeRepository?.let { repo ->
            val fridgeItems = repo.getAllFridgeItems().first()
            val fArray = JSONArray()
            for (f in fridgeItems) {
                val obj = JSONObject()
                obj.put("id", f.id)
                obj.put("name", f.name)
                obj.put("category", f.category.name)
                obj.put("quantity", f.quantity)
                obj.put("unit", f.unit)
                obj.put("purchaseDate", f.purchaseDate.toString())
                obj.put("status", f.status.name)
                // Older app versions read only isConsumed
                obj.put("isConsumed", !f.isActive)
                if (f.expenseId != null) obj.put("expenseId", f.expenseId)
                obj.put("createdAt", f.createdAt)
                obj.put("storage", f.storage.name)
                f.price?.let { obj.put("price", it) }
                f.estimatedExpiry?.let { obj.put("estimatedExpiry", it.toString()) }
                f.endedAt?.let { obj.put("endedAt", it) }
                f.storedOn?.let { obj.put("storedOn", it.toString()) }
                fArray.put(obj)
            }
            root.put("fridgeItems", fArray)
        }

        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(root.toString(2))
        }
    }

    suspend fun importFromJson(inputStream: InputStream): ImportResult = withContext(Dispatchers.IO) {
        val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(jsonString)

        val appName = root.optString("app")
        if (appName != "local-budget") {
            return@withContext ImportResult(success = false, message = "無効なバックアップファイルです")
        }

        var importedCategories = 0
        var importedExpenses = 0
        var importedRecurring = 0

        // Import Categories
        val catArray = root.optJSONArray("categories")
        val categoryIdMap = mutableMapOf<Long, Long>() // Old ID to New ID
        if (catArray != null) {
            val existing = categoryRepository.getAllCategories().first().associateBy { it.name }
            for (i in 0 until catArray.length()) {
                val obj = catArray.getJSONObject(i)
                val oldId = obj.getLong("id")
                val name = obj.getString("name")
                val existingCat = existing[name]
                if (existingCat != null) {
                    categoryIdMap[oldId] = existingCat.id
                } else {
                    val newCat = Category(
                        name = name,
                        iconName = obj.optString("iconName", "Category"),
                        colorHex = obj.optString("colorHex", "#607D8B"),
                        budgetLimit = if (obj.has("budgetLimit")) obj.getLong("budgetLimit") else null,
                        isDefault = obj.optBoolean("isDefault", false)
                    )
                    val newId = categoryRepository.insertCategory(newCat)
                    categoryIdMap[oldId] = newId
                    importedCategories++
                }
            }
        }

        val defaultCatId = categoryRepository.getAllCategories().first().firstOrNull()?.id ?: 1L

        // Import Expenses
        val expArray = root.optJSONArray("expenses")
        if (expArray != null) {
            for (i in 0 until expArray.length()) {
                val obj = expArray.getJSONObject(i)
                val oldCatId = obj.getLong("categoryId")
                val mappedCatId = categoryIdMap[oldCatId] ?: defaultCatId

                val expense = Expense(
                    date = LocalDate.parse(obj.getString("date")),
                    amount = obj.getLong("amount"),
                    storeName = obj.optString("storeName", ""),
                    categoryId = mappedCatId,
                    memo = obj.optString("memo", ""),
                    sourceType = try {
                        SourceType.valueOf(obj.optString("sourceType", SourceType.MANUAL.name))
                    } catch (_: Exception) {
                        SourceType.MANUAL
                    },
                    hasReceiptImage = obj.optBoolean("hasReceiptImage", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
                expenseRepository.insertExpenseWithItems(expense, receiptItemsFromJson(obj.optJSONArray("items")))
                importedExpenses++
            }
        }

        // Import Recurring Expenses
        val recArray = root.optJSONArray("recurringExpenses")
        if (recArray != null) {
            for (i in 0 until recArray.length()) {
                val obj = recArray.getJSONObject(i)
                val oldCatId = obj.getLong("categoryId")
                val mappedCatId = categoryIdMap[oldCatId] ?: defaultCatId

                val rec = RecurringExpense(
                    name = obj.getString("name"),
                    amount = obj.getLong("amount"),
                    categoryId = mappedCatId,
                    dayOfMonth = obj.optInt("dayOfMonth", 1),
                    isActive = obj.optBoolean("isActive", true),
                    memo = obj.optString("memo", "")
                )
                recurringRepository.insertRecurringExpense(rec)
                importedRecurring++
            }
        }

        // Import Fridge Items
        var importedFridge = 0
        val fridgeArray = root.optJSONArray("fridgeItems")
        if (fridgeArray != null && fridgeRepository != null) {
            for (i in 0 until fridgeArray.length()) {
                val obj = fridgeArray.getJSONObject(i)
                val cat = try {
                    FoodCategory.valueOf(obj.optString("category", FoodCategory.OTHER.name))
                } catch (_: Exception) {
                    FoodCategory.OTHER
                }
                val item = FridgeItem(
                    name = obj.getString("name"),
                    category = cat,
                    quantity = obj.optInt("quantity", 1),
                    unit = obj.optString("unit", "個"),
                    purchaseDate = try {
                        LocalDate.parse(obj.getString("purchaseDate"))
                    } catch (_: Exception) {
                        LocalDate.now()
                    },
                    status = when {
                        obj.has("status") -> FridgeItemStatus.fromString(obj.getString("status"))
                        obj.optBoolean("isConsumed", false) -> FridgeItemStatus.CONSUMED
                        else -> FridgeItemStatus.ACTIVE
                    },
                    expenseId = if (obj.has("expenseId")) obj.getLong("expenseId") else null,
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    price = if (obj.has("price")) obj.getLong("price") else null,
                    storage = StorageLocation.fromString(obj.optString("storage")),
                    estimatedExpiry = obj.optString("estimatedExpiry").takeIf { it.isNotEmpty() }
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    endedAt = if (obj.has("endedAt")) obj.getLong("endedAt") else null,
                    storedOn = obj.optString("storedOn").takeIf { it.isNotEmpty() }
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                )
                fridgeRepository.insertFridgeItem(item)
                importedFridge++
            }
        }

        val msg = buildString {
            append("復元完了: 支出${importedExpenses}件、固定支出${importedRecurring}件、カテゴリ${importedCategories}件")
            if (importedFridge > 0) {
                append("、冷蔵庫食材${importedFridge}件")
            }
            append("をインポートしました")
        }

        ImportResult(
            success = true,
            message = msg,
            expenseCount = importedExpenses,
            recurringCount = importedRecurring,
            fridgeCount = importedFridge
        )
    }

    companion object {
        fun receiptItemsToJson(items: List<ReceiptLineItem>): JSONArray {
            val array = JSONArray()
            for (item in items) {
                val obj = JSONObject()
                obj.put("name", item.name)
                item.price?.let { obj.put("price", it) }
                obj.put("quantity", item.quantity)
                item.unitPrice?.let { obj.put("unitPrice", it) }
                obj.put("discount", item.discount)
                array.put(obj)
            }
            return array
        }

        /** Backups from before receipt lines were saved have no items array. */
        fun receiptItemsFromJson(array: JSONArray?): List<ReceiptLineItem> {
            if (array == null) return emptyList()
            return (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ReceiptLineItem(
                    name = obj.getString("name"),
                    price = if (obj.has("price")) obj.getLong("price") else null,
                    quantity = obj.optInt("quantity", 1),
                    unitPrice = if (obj.has("unitPrice")) obj.getLong("unitPrice") else null,
                    discount = obj.optLong("discount", 0)
                )
            }
        }
    }

    data class ImportResult(
        val success: Boolean,
        val message: String,
        val expenseCount: Int = 0,
        val recurringCount: Int = 0,
        val fridgeCount: Int = 0
    )
}
