package com.localbudget.app.domain.statement

import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.SourceType
import com.localbudget.app.domain.text.JapaneseTextNormalizer
import java.time.LocalDate

/** What a statement row already matches among the saved expenses: the same day and amount. */
enum class StatementMatch(val label: String?) {
    NONE(null),
    /** The receipt says more (items, the store as printed), so it is kept and the row left out. */
    RECEIPT("レシートで登録済み"),
    ALREADY_IMPORTED("取り込み済み"),
    OTHER_FILE("別のCSVに同じ明細あり"),
    OTHER("同じ日・金額の支出あり")
}

data class StatementCandidate(
    val row: StatementRow,
    val categoryId: Long,
    val match: StatementMatch,
    val selected: Boolean = match == StatementMatch.NONE
) {
    fun toExpense(): Expense = Expense(
        date = row.date,
        amount = row.amount,
        storeName = row.storeName,
        categoryId = categoryId,
        sourceType = SourceType.CARD_CSV
    )
}

/** A refund in this statement of a purchase imported from an earlier one: that expense is removed. */
data class RefundTarget(
    val refund: StatementRow,
    val expense: Expense,
    val selected: Boolean = true
)

data class StatementPlan(
    val candidates: List<StatementCandidate>,
    val refundTargets: List<RefundTarget>,
    /** Refunds whose purchase was found neither in the file nor among the imported expenses. */
    val unmatchedRefunds: Int,
    val cancelledCount: Int
)

/** Turns a parsed statement into what to review: duplicates found and unticked, categories guessed. */
object StatementImportPlanner {

    fun plan(statement: ParsedStatement, existing: List<Expense>, categories: List<Category>): StatementPlan {
        // Each saved expense accounts for one row only: two ¥150 coffees on one day are two purchases
        val unmatched = existing.groupByTo(mutableMapOf()) { DayAmount(it.date, it.amount) }
        val guesser = StoreCategoryGuesser(existing, categories)

        val candidates = statement.rows.map { row ->
            val sameDayAndAmount = unmatched[DayAmount(row.date, row.amount)]
            val matched = sameDayAndAmount?.let { pickMatch(it, row) }
            if (matched != null) sameDayAndAmount.remove(matched)
            StatementCandidate(
                row = row,
                categoryId = guesser.guess(row.storeName),
                match = when (matched?.sourceType) {
                    null -> StatementMatch.NONE
                    SourceType.RECEIPT_OCR -> StatementMatch.RECEIPT
                    SourceType.CARD_CSV -> StatementMatch.ALREADY_IMPORTED
                    else -> StatementMatch.OTHER
                }
            )
        }

        val refundTargets = refundTargets(statement, existing)
        return StatementPlan(
            candidates = candidates,
            refundTargets = refundTargets,
            unmatchedRefunds = statement.refunds.size - refundTargets.size,
            cancelledCount = statement.cancelledCount
        )
    }

    /**
     * The imported expenses that this statement's leftover refunds pay back: same store and amount,
     * dated before everything in this file, so importing the same statement twice never removes
     * a purchase listed in it.
     */
    private fun refundTargets(statement: ParsedStatement, existing: List<Expense>): List<RefundTarget> {
        val start = (statement.rows + statement.refunds).minOfOrNull { it.date } ?: return emptyList()
        val imported = existing
            .filter { it.sourceType == SourceType.CARD_CSV && it.date.isBefore(start) }
            .toMutableList()
        return statement.refunds.mapNotNull { refund ->
            val store = JapaneseTextNormalizer.looseKey(refund.storeName)
            val paidBack = imported
                .filter { it.amount == refund.amount && JapaneseTextNormalizer.looseKey(it.storeName) == store }
                .maxByOrNull { it.date } ?: return@mapNotNull null
            imported.remove(paidBack)
            RefundTarget(refund, paidBack)
        }
    }

    /** An earlier import of this row first, then a receipt, then anything else of that day and amount. */
    private fun pickMatch(candidates: List<Expense>, row: StatementRow): Expense? {
        val storeKey = JapaneseTextNormalizer.looseKey(row.storeName)
        return candidates.find { it.sourceType == SourceType.CARD_CSV && JapaneseTextNormalizer.looseKey(it.storeName) == storeKey }
            ?: candidates.find { it.sourceType == SourceType.CARD_CSV }
            ?: candidates.find { it.sourceType == SourceType.RECEIPT_OCR }
            ?: candidates.firstOrNull()
    }

    private data class DayAmount(val date: LocalDate, val amount: Long)
}
