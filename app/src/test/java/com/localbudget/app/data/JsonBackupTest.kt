package com.localbudget.app.data

import com.localbudget.app.data.backup.JsonBackupManager
import com.localbudget.app.domain.model.ReceiptLineItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonBackupTest {

    @Test
    fun testJsonBackupStructureAndParsing() {
        val json = """
            {
              "app": "local-budget",
              "version": 1,
              "exportedAt": 1727244000000,
              "categories": [
                {
                  "id": 1,
                  "name": "食費",
                  "iconName": "Restaurant",
                  "colorHex": "#FF5722",
                  "budgetLimit": 50000,
                  "isDefault": true
                }
              ],
              "expenses": [
                {
                  "id": 10,
                  "date": "2026-09-25",
                  "amount": 1280,
                  "storeName": "セブン-イレブン",
                  "categoryId": 1,
                  "memo": "昼食",
                  "sourceType": "RECEIPT_OCR",
                  "hasReceiptImage": false,
                  "createdAt": 1727244000000,
                  "updatedAt": 1727244000000
                }
              ],
              "recurringExpenses": [
                {
                  "id": 1,
                  "name": "Netflix",
                  "amount": 1490,
                  "categoryId": 1,
                  "dayOfMonth": 15,
                  "isActive": true,
                  "memo": "サブスク",
                  "createdAt": 1727244000000
                }
              ],
              "fridgeItems": [
                {
                  "id": 1,
                  "name": "キャベツ",
                  "category": "VEGETABLE",
                  "quantity": 1,
                  "unit": "個",
                  "purchaseDate": "2026-09-25",
                  "isConsumed": false,
                  "createdAt": 1727244000000
                }
              ]
            }
        """.trimIndent()

        val root = JSONObject(json)
        assertEquals("local-budget", root.getString("app"))
        assertEquals(1, root.getInt("version"))

        val categories = root.getJSONArray("categories")
        assertEquals(1, categories.length())
        val cat = categories.getJSONObject(0)
        assertEquals("食費", cat.getString("name"))
        assertEquals(50000L, cat.getLong("budgetLimit"))

        val expenses = root.getJSONArray("expenses")
        assertEquals(1, expenses.length())
        val exp = expenses.getJSONObject(0)
        assertEquals("2026-09-25", exp.getString("date"))
        assertEquals(1280L, exp.getLong("amount"))
        assertEquals("RECEIPT_OCR", exp.getString("sourceType"))

        val recurring = root.getJSONArray("recurringExpenses")
        assertEquals(1, recurring.length())
        val rec = recurring.getJSONObject(0)
        assertEquals("Netflix", rec.getString("name"))
        assertEquals(1490L, rec.getLong("amount"))
        assertEquals(15, rec.getInt("dayOfMonth"))
        assertTrue(rec.getBoolean("isActive"))

        val fridge = root.getJSONArray("fridgeItems")
        assertEquals(1, fridge.length())
        val item = fridge.getJSONObject(0)
        assertEquals("キャベツ", item.getString("name"))
        assertEquals("VEGETABLE", item.getString("category"))
        assertEquals(1, item.getInt("quantity"))
        assertEquals("個", item.getString("unit"))
        assertEquals("2026-09-25", item.getString("purchaseDate"))
        org.junit.Assert.assertFalse(item.getBoolean("isConsumed"))
    }

    @Test
    fun receiptItemsRoundTrip() {
        val items = listOf(
            ReceiptLineItem(name = "ｷｬﾍﾞﾂ", price = 198, discount = 30),
            ReceiptLineItem(name = "牛乳", price = 476, quantity = 2, unitPrice = 238),
            ReceiptLineItem(name = "ﾚｼﾞ袋")
        )

        val json = JsonBackupManager.receiptItemsToJson(items).toString()

        assertEquals(items, JsonBackupManager.receiptItemsFromJson(JSONArray(json)))
    }

    @Test
    fun expenseWithoutItemsRestoresNoLines() {
        assertTrue(JsonBackupManager.receiptItemsFromJson(null).isEmpty())
    }
}
