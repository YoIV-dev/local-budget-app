package com.localbudget.app.domain.model

/**
 * One purchased line on a receipt.
 * [name] is kept exactly as printed. [price] is the amount printed for the line (all units,
 * before any discount row), null when no price could be tied to the name.
 */
data class ReceiptLineItem(
    val name: String,
    val price: Long? = null,
    val quantity: Int = 1,
    val unitPrice: Long? = null,
    val discount: Long = 0
) {
    val netPrice: Long? get() = price?.let { it - discount }

    /** This line with [price] corrected by hand; a unit price that no longer adds up to it is dropped. */
    fun withPrice(price: Long?): ReceiptLineItem =
        copy(price = price, unitPrice = unitPrice?.takeIf { price != null && it * quantity == price })
}
