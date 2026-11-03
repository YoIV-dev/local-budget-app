package com.localbudget.app.domain.model

enum class SourceType(val displayName: String) {
    MANUAL("手動入力"),
    RECEIPT_OCR("レシートOCR"),
    /** No longer created: kept so expenses read from payment-notification screenshots keep their label. */
    SCREENSHOT_OCR("決済通知OCR"),
    CARD_CSV("カード明細")
}
