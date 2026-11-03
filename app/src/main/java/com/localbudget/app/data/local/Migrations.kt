package com.localbudget.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.localbudget.app.domain.food.FridgeItemEnricher
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeEventSource
import com.localbudget.app.domain.model.FridgeEventType
import com.localbudget.app.domain.model.FridgeItem
import java.time.LocalDate

/**
 * v3 → v4: fridge items gain food key, price, storage, estimated expiry, status (replacing
 * is_consumed) and end time; the fridge_events log is added.
 *
 * SQLite before 3.35 (Android 14) cannot drop columns, so fridge_items is rebuilt. Existing
 * items get their derived fields and an ADDED event at their creation time; their end times
 * were never recorded, so consumed items get no end event and are left out of learning.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `fridge_items_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `category` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `unit` TEXT NOT NULL, " +
                "`purchase_epoch_day` INTEGER NOT NULL, `status` TEXT NOT NULL DEFAULT 'ACTIVE', `expense_id` INTEGER, " +
                "`created_at` INTEGER NOT NULL, `normalized_name` TEXT NOT NULL DEFAULT '', `price` INTEGER, " +
                "`storage` TEXT NOT NULL DEFAULT 'FRIDGE', `expiry_epoch_day` INTEGER, `ended_at` INTEGER)"
        )
        db.execSQL(
            "INSERT INTO `fridge_items_new` (`id`, `name`, `category`, `quantity`, `unit`, `purchase_epoch_day`, " +
                "`status`, `expense_id`, `created_at`) " +
                "SELECT `id`, `name`, `category`, `quantity`, `unit`, `purchase_epoch_day`, " +
                "CASE `is_consumed` WHEN 1 THEN 'CONSUMED' ELSE 'ACTIVE' END, `expense_id`, `created_at` FROM `fridge_items`"
        )
        db.execSQL("DROP TABLE `fridge_items`")
        db.execSQL("ALTER TABLE `fridge_items_new` RENAME TO `fridge_items`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fridge_items_purchase_epoch_day` ON `fridge_items` (`purchase_epoch_day`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fridge_items_category` ON `fridge_items` (`category`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fridge_items_status` ON `fridge_items` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fridge_items_normalized_name` ON `fridge_items` (`normalized_name`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `fridge_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`item_id` INTEGER NOT NULL, `food_key` TEXT NOT NULL, `category` TEXT NOT NULL, `storage` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `source` TEXT NOT NULL, `occurred_at` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fridge_events_item_id` ON `fridge_events` (`item_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fridge_events_food_key` ON `fridge_events` (`food_key`)")

        backfill(db)
    }

    private fun backfill(db: SupportSQLiteDatabase) {
        val rows = mutableListOf<Array<Any?>>()
        db.query("SELECT `id`, `name`, `category`, `purchase_epoch_day`, `expense_id`, `created_at` FROM `fridge_items`").use { c ->
            while (c.moveToNext()) {
                rows.add(arrayOf(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), if (c.isNull(4)) null else c.getLong(4), c.getLong(5)))
            }
        }
        for (row in rows) {
            val id = row[0] as Long
            val name = row[1] as String
            val enriched = FridgeItemEnricher.enrich(
                FridgeItem(
                    name = name,
                    category = FoodCategory.fromString(row[2] as String),
                    purchaseDate = LocalDate.ofEpochDay(row[3] as Long),
                    storage = FridgeItemEnricher.guessStorage(name)
                )
            )
            db.execSQL(
                "UPDATE `fridge_items` SET `normalized_name` = ?, `storage` = ?, `expiry_epoch_day` = ? WHERE `id` = ?",
                arrayOf(enriched.normalizedName, enriched.storage.name, enriched.estimatedExpiry?.toEpochDay(), id)
            )
            val source = if (row[4] != null) FridgeEventSource.RECEIPT else FridgeEventSource.USER
            db.execSQL(
                "INSERT INTO `fridge_events` (`item_id`, `food_key`, `category`, `storage`, `type`, `source`, `occurred_at`) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf(id, enriched.normalizedName, enriched.category.name, enriched.storage.name, FridgeEventType.ADDED.name, source.name, row[5])
            )
        }
    }
}

/** v4 → v5: fridge items record the day they were moved into their current storage. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `fridge_items` ADD COLUMN `stored_epoch_day` INTEGER")
    }
}

/** v5 → v6: the lines read from a receipt are kept with its expense. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `receipt_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`expense_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, `price` INTEGER, " +
                "`quantity` INTEGER NOT NULL, `unit_price` INTEGER, `discount` INTEGER NOT NULL, " +
                "FOREIGN KEY(`expense_id`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipt_items_expense_id` ON `receipt_items` (`expense_id`)")
    }
}
