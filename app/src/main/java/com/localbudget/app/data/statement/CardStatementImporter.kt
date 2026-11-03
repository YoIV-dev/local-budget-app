package com.localbudget.app.data.statement

import android.content.ContentResolver
import android.net.Uri
import com.localbudget.app.domain.statement.CardStatementParser
import com.localbudget.app.domain.statement.RefundTarget
import com.localbudget.app.domain.statement.StatementCandidate
import com.localbudget.app.domain.statement.StatementImportPlanner
import com.localbudget.app.domain.statement.StatementPlan
import com.localbudget.app.domain.statement.StatementBatchPlanner
import com.localbudget.app.domain.repository.CategoryRepository
import com.localbudget.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

class CardStatementImporter(
    private val contentResolver: ContentResolver,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository
) {

    /** Reads all selected files before preparing a single review. */
    suspend fun read(uris: List<Uri>): StatementPlan = withContext(Dispatchers.IO) {
        val statements = uris.map { uri ->
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_BYTES) throw IOException("Statement file is larger than $MAX_BYTES bytes")
            }
            out.toByteArray()
        } ?: throw IOException("Could not open $uri")
            CardStatementParser.parse(StatementTextDecoder.decode(bytes))
        }
        StatementBatchPlanner.plan(
            statements,
            existing = expenseRepository.getAllExpenses().first(),
            categories = categoryRepository.getAllCategories().first()
        )
    }

    /** Adds [candidates] and removes the expenses that [refunds] paid back. */
    suspend fun save(candidates: List<StatementCandidate>, refunds: List<RefundTarget>) {
        expenseRepository.insertExpenses(candidates.map { it.toExpense() })
        refunds.forEach { expenseRepository.deleteExpenseById(it.expense.id) }
    }

    private companion object {
        // A year of statements is a few hundred kilobytes
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}

/** Japanese card issuers ship statements in Shift_JIS or in UTF-8 (often with a BOM). */
object StatementTextDecoder {
    private val SHIFT_JIS: Charset = Charset.forName("windows-31j")

    fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
            .removePrefix("\uFEFF")
    } catch (_: CharacterCodingException) {
        String(bytes, SHIFT_JIS)
    }
}
