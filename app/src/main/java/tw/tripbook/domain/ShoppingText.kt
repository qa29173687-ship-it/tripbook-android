package tw.tripbook.domain

object ShoppingText {
    /** Blank name is fine when there is a photo. */
    fun itemName(name: String, hasPhoto: Boolean): String =
        name.trim().ifBlank { if (hasPhoto) "照片裡的東西" else "要買的東西" }

    fun expenseTitle(name: String, qty: Int): String {
        val n = name.trim().ifBlank { "購物" }
        return if (qty > 1) "$n ×$qty" else n
    }
}
