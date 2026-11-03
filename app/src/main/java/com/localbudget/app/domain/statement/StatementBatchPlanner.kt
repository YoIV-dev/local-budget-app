package com.localbudget.app.domain.statement

import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.Expense

/** Keeps possible overlaps visible for review while preserving repeated purchases within a file. */
object StatementBatchPlanner {
    fun plan(statements: List<ParsedStatement>, existing: List<Expense>, categories: List<Category>): StatementPlan {
        val previousCounts = mutableMapOf<StatementRow, Int>()
        val duplicateFlags = mutableListOf<Boolean>()
        val rows = mutableListOf<StatementRow>()
        val refunds = mutableListOf<StatementRow>()
        val refundCounts = mutableMapOf<StatementRow, Int>()
        statements.forEach { statement ->
            val counts = mutableMapOf<StatementRow, Int>()
            statement.rows.forEach { row ->
                val occurrence = (counts[row] ?: 0) + 1
                counts[row] = occurrence
                rows += row
                duplicateFlags += occurrence <= (previousCounts[row] ?: 0)
            }
            counts.forEach { (row, count) -> previousCounts[row] = maxOf(previousCounts[row] ?: 0, count) }
            val currentRefunds = mutableMapOf<StatementRow, Int>()
            statement.refunds.forEach { refund ->
                val occurrence = (currentRefunds[refund] ?: 0) + 1
                currentRefunds[refund] = occurrence
                if (occurrence > (refundCounts[refund] ?: 0)) refunds += refund
            }
            currentRefunds.forEach { (row, count) -> refundCounts[row] = maxOf(refundCounts[row] ?: 0, count) }
        }
        val result = StatementImportPlanner.plan(
            ParsedStatement(rows, statements.sumOf { it.cancelledCount }, refunds), existing, categories
        )
        return result.copy(candidates = result.candidates.mapIndexed { index, candidate ->
            if (duplicateFlags[index] && candidate.match == StatementMatch.NONE) {
                candidate.copy(match = StatementMatch.OTHER_FILE, selected = false)
            } else candidate
        })
    }
}
