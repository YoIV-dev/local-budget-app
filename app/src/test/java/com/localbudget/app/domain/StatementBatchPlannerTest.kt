package com.localbudget.app.domain

import com.localbudget.app.domain.statement.ParsedStatement
import com.localbudget.app.domain.statement.StatementBatchPlanner
import com.localbudget.app.domain.statement.StatementMatch
import com.localbudget.app.domain.statement.StatementRow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatementBatchPlannerTest {
    @Test
    fun overlappingFilesFlagDuplicatesButPreserveRepeatedPurchasesInOneFile() {
        val row = StatementRow(LocalDate.of(2026, 10, 5), "店", 500)
        val plan = StatementBatchPlanner.plan(
            listOf(ParsedStatement(listOf(row, row)), ParsedStatement(listOf(row, row, row))),
            emptyList(), emptyList()
        )
        assertEquals(listOf(true, true, false, false, true), plan.candidates.map { it.selected })
        assertEquals(2, plan.candidates.count { it.match == StatementMatch.OTHER_FILE })
    }
}
