package tw.tripbook.util

data class CurrencyOption(val code: String, val label: String)

object Currencies {
    val options = listOf(
        CurrencyOption("TWD", "台幣 TWD"),
        CurrencyOption("USD", "美金 USD"),
        CurrencyOption("JPY", "日幣 JPY")
    )
    fun labelOf(code: String): String =
        options.firstOrNull { it.code == code }?.label ?: code
}
