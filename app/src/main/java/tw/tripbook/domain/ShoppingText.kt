package tw.tripbook.domain

object ShoppingText {
    fun expenseTitle(name: String, qty: Int): String {
        val n = name.trim().ifBlank { "購物" }
        return if (qty > 1) "$n ×$qty" else n
    }
}
