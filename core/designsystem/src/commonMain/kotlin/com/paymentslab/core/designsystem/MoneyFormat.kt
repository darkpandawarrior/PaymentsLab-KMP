package com.paymentslab.core.designsystem

import com.siddharth.kmp.common.minorToDecimalString
import com.siddharth.kmp.paymentsapi.Money

/**
 * Presentation-only formatting of [Money] into a human string. Formatting lives at the UI edge —
 * never in the [Money] value type — per the money-handling rule the whole app teaches.
 */
fun Money.format(): String = formatMoney(amountMinor, currency)

/**
 * The single money formatter ([Money.format] and [AnimatedAmount] both route here): symbol, digit
 * grouping, two decimals. INR groups the Indian way (2,49,900.00), every other currency in
 * thousands. The sign leads the symbol ("-₹10.50"). The currency-code fallback keeps this correct
 * for every ISO code.
 */
internal fun formatMoney(
    amountMinor: Long,
    currency: String,
): String {
    val symbol =
        when (currency) {
            "INR" -> "₹"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> "$currency "
        }
    val plain = amountMinor.minorToDecimalString()
    val negative = plain.startsWith("-")
    val unsigned = plain.removePrefix("-")
    val whole = unsigned.substringBefore('.')
    val fraction = unsigned.substringAfter('.')
    val grouped = if (currency == "INR") groupIndian(whole) else groupThousands(whole)
    return (if (negative) "-" else "") + symbol + grouped + "." + fraction
}

private fun groupThousands(digits: String): String =
    digits
        .reversed()
        .chunked(GroupSize)
        .joinToString(",")
        .reversed()

private fun groupIndian(digits: String): String {
    if (digits.length <= GroupSize) return digits
    val head = digits.dropLast(GroupSize)
    val tail = digits.takeLast(GroupSize)
    val headGrouped =
        head
            .reversed()
            .chunked(IndianHeadGroupSize)
            .joinToString(",")
            .reversed()
    return "$headGrouped,$tail"
}

private const val GroupSize = 3
private const val IndianHeadGroupSize = 2
