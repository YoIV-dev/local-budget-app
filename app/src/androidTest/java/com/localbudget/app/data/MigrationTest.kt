package com.localbudget.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localbudget.app.data.local.AppDatabase
import com.localbudget.app.data.local.MIGRATION_3_4
import com.localbudget.app.data.local.MIGRATION_4_5
import com.localbudget.app.data.local.MIGRATION_5_6
import com.localbudget.app.domain.food.FridgeItemEnricher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate3To4KeepsFridgeItemsAndBackfills() {
        val purchase = LocalDate.of(2026, 10, 1).toEpochDay()
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO fridge_items (id, name, category, quantity, unit, purchase_epoch_day, is_consumed, expense_id, created_at) " +
                    "VALUES (1, 'ｷｬﾍﾞﾂ', 'VEGETABLE', 1, '', $purchase, 0, 5, 1000), " +
                    "(2, '冷凍 鶏もも肉', 'MEAT', 2, 'P', $purchase, 1, NULL, 2000)"
            )
        }

        // Also validates the migrated schema against the v4 entities
        val db = helper.runMigrationsAndValidate(DB, 4, true, MIGRATION_3_4)

        db.query("SELECT id, status, normalized_name, storage, expiry_epoch_day, quantity FROM fridge_items ORDER BY id").use { c ->
            assertTrue(c.moveToNext())
            assertEquals("ACTIVE", c.getString(1))
            assertEquals(FridgeItemEnricher.foodKey("キャベツ"), c.getString(2))
            assertEquals("FRIDGE", c.getString(3))
            assertEquals(purchase + 14, c.getLong(4))
            assertTrue(c.moveToNext())
            assertEquals("CONSUMED", c.getString(1))
            assertEquals("FREEZER", c.getString(3))
            assertEquals(purchase + 30, c.getLong(4))
            assertEquals(2, c.getInt(5))
        }
        db.query("SELECT item_id, type, source, occurred_at FROM fridge_events ORDER BY item_id").use { c ->
            assertTrue(c.moveToNext())
            assertEquals("RECEIPT", c.getString(2))
            assertEquals(1000L, c.getLong(3))
            assertTrue(c.moveToNext())
            assertEquals("ADDED", c.getString(1))
            assertEquals("USER", c.getString(2))
        }
    }

    @Test
    fun migrate4To5AddsStoredDayAsUnset() {
        helper.createDatabase(DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO fridge_items (id, name, category, quantity, unit, purchase_epoch_day, created_at, storage) " +
                    "VALUES (1, '鶏もも肉', 'MEAT', 1, 'パック', 20000, 1000, 'FREEZER')"
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 5, true, MIGRATION_4_5)

        db.query("SELECT storage, stored_epoch_day FROM fridge_items").use { c ->
            assertTrue(c.moveToNext())
            assertEquals("FREEZER", c.getString(0))
            assertTrue(c.isNull(1))
        }
    }

    @Test
    fun migrate5To6AddsReceiptItemsThatFollowTheirExpense() {
        helper.createDatabase(DB, 5).use { db ->
            db.execSQL("INSERT INTO categories (id, name, icon_name, color_hex, is_default) VALUES (1, '食費', 'Restaurant', '#FF5722', 1)")
            db.execSQL(
                "INSERT INTO expenses (id, epoch_day, year_month, amount, store_name, category_id, memo, source_type, " +
                    "has_receipt_image, created_at, updated_at) VALUES (1, 20000, '2024-10', 674, 'スーパー', 1, '', 'RECEIPT_OCR', 0, 1000, 1000)"
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 6, true, MIGRATION_5_6)

        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL(
            "INSERT INTO receipt_items (expense_id, position, name, price, quantity, unit_price, discount) " +
                "VALUES (1, 0, '牛乳', 476, 2, 238, 0)"
        )
        db.execSQL("DELETE FROM expenses WHERE id = 1")
        db.query("SELECT COUNT(*) FROM receipt_items").use { c ->
            assertTrue(c.moveToNext())
            assertEquals(0, c.getInt(0))
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
