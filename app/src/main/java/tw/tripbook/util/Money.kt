package tw.tripbook.util

import java.text.NumberFormat
import java.util.Locale

object Money {
    private val nf = NumberFormat.getNumberInstance(Locale.TAIWAN)
    fun format(amount: Long, currency: String = "TWD"): String {
        val n = nf.format(amount)
        return when (currency) {
            "TWD", "NTD" -> "NT\$$n"
            "JPY" -> "¥$n"
            "USD" -> "US\$$n"
            else -> "$currency $n"
        }
    }
    fun signed(amount: Long, currency: String = "TWD"): String {
        val abs = format(kotlin.math.abs(amount), currency)
        return when {
            amount > 0 -> "+$abs"
            amount < 0 -> "-$abs"
            else -> abs
        }
    }
}
