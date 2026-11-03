package com.localbudget.app.domain.model

import com.localbudget.app.domain.ocr.OcrLine
import com.localbudget.app.domain.ocr.TotalCheck
import java.time.LocalDate

/**
 * [rawText] is the text the parsers read (rows rebuilt from [ocrLines] when available);
 * [ocrLines] are ML Kit's lines with positions, empty for text-only input;
 * [totalCheck] says how far [amount] can be trusted.
 */
data class ParsedReceipt(
    val storeName: String,
    val date: LocalDate,
    val amount: Long,
    val suggestedCategoryId: Long? = null,
    val rawText: String = "",
    val confidenceNotes: List<String> = emptyList(),
    val ocrLines: List<OcrLine> = emptyList(),
    val totalCheck: TotalCheck = TotalCheck()
) {
    /** What the confirmation screen should say about [amount], or null when it needs no checking. */
    val amountWarning: String? get() = totalCheck.warning(amount)
}
