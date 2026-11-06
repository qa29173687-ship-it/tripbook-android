package tw.tripbook.domain

enum class ExpenseCategory(val label: String) {
    STAY("住宿"), TRANSIT("交通"), FOOD("餐飲"), TICKET("門票"), SHOP("購物"), OTHER("其他")
}

enum class PaymentMethod(val label: String) {
    CASH("現金"), CARD("刷卡"), TRANSFER("轉帳"), POOL("公款")
}

enum class CashKind(val label: String) {
    TOP_UP("儲值"), EXCHANGE("換匯"), ATM("提款"), PERSONAL("自己花"), OTHER("其他"),
    EXPENSE("分帳支出"), EXPENSE_REVERSE("分帳退回")
}

enum class CashDirection { IN, OUT }

data class SplitShare(val memberId: Long, val amountCents: Long)

data class Transfer(val fromMemberId: Long, val toMemberId: Long, val amountCents: Long)

data class MemberBalance(
    val memberId: Long,
    val paidCents: Long,
    val shareCents: Long,
    val netCents: Long // paid - share; positive = owed money
)

data class CashTimelineEntry(
    val id: Long,
    val direction: CashDirection,
    val kind: CashKind,
    val amountCents: Long,
    val note: String,
    val occurredAt: Long,
    val balanceAfterCents: Long,
    val relatedExpenseId: Long? = null
)
