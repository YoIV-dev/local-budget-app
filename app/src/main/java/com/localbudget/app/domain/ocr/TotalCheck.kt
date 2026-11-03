package com.localbudget.app.domain.ocr

import com.localbudget.app.domain.text.JapaneseTextNormalizer
import java.util.Locale

/**
 * How far a receipt's total can be trusted, so the confirmation screen can point the user at it.
 *
 * [corroborated]: another figure on the receipt is the same amount (another total row, the card /
 * e-money line, cash paid − change, subtotal (+ tax), the items). [asRead]: the total as printed, when
 * a yen sign read as its first digit was dropped (¥1,969 read as 71,969). [confidence]: ML Kit's
 * confidence for the row the total was read from, when known.
 */
data class TotalCheck(
    val corroborated: Boolean = false,
    val asRead: Long? = null,
    val confidence: Float? = null
) {
    /**
     * What to tell the user about the total [amount], or null when nothing needs checking.
     * A total that agrees with the rest of the receipt is trusted however faint it was;
     * one that cannot be compared is pointed out unless ML Kit read it clearly.
     */
    fun warning(amount: Long): String? = when {
        amount <= 0 -> "合計を読み取れませんでした。金額を入力してください"
        asRead != null -> String.format(
            Locale.JAPAN, "合計を ¥%,d と読みましたが、ほかの金額と合う ¥%,d にしました。レシートと見比べてください", asRead, amount
        )
        corroborated -> null
        confidence == null || confidence < LOW_CONFIDENCE ->
            "合計がはっきり読めず、ほかの金額とも照合できませんでした。レシートと見比べてください"
        else -> null
    }

    companion object {
        /**
         * Below this ML Kit line confidence, a total nothing else on the receipt agrees with is pointed out.
         * The Japanese model rates even clean receipt lines low (Pixel 9a scans: median 0.71, totals read
         * right at 0.60–0.77), so this sits near the bottom fifth of lines rather than at a "sure" level.
         */
        const val LOW_CONFIDENCE = 0.6f

        private val NUMBER = Regex("""(?<![0-9,])(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?![0-9,])""")

        /** The lowest ML Kit confidence among the [lines] that show [amount], or null when none of them says. */
        fun confidenceOf(lines: List<OcrLine>, amount: Long): Float? = lines
            .filter { line ->
                val text = OcrTextRepair.repairLine(JapaneseTextNormalizer.nfkc(OcrTextRepair.repairKana(line.text)))
                NUMBER.findAll(text).any { it.value.replace(",", "").toLongOrNull() == amount }
            }
            .mapNotNull { it.confidence }
            .minOrNull()
    }
}
