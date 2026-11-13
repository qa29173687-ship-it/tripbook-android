package tw.tripbook.domain

/** Pure models + math for the shared (cloud) trip, used by companions' read-only view. */
data class CMember(val id: String, val name: String, val startCents: Long, val cashCents: Long, val isOwner: Boolean)

data class CExpense(
    val id: String, val title: String, val amountCents: Long, val payerId: String,
    val method: String, val at: Long, val shares: Map<String, Long>
)

/** A companion purchase not yet recorded by the owner's phone. */
/** memberId = who paid; forId = whose item / whose share. */
data class CPending(
    val memberId: String, val title: String, val amountCents: Long, val method: String, val at: Long,
    val forId: String = memberId, val itemId: String = "", val repaidLabel: String? = null
)

/** A helper-pay marked 已當場還錢 on the shopping item (may not be ingested yet). */
data class CRepaidItem(
    val itemId: String,
    val expenseId: String?,
    val payerId: String,
    val payerName: String,
    val forId: String,
    val forName: String,
    val title: String,
    val amountCents: Long,
    val originalMethod: String,
    val repayMethod: String
)

data class CTransfer(val fromId: String, val toId: String, val amountCents: Long)

data class MySummary(
    val remainingCash: Long,
    val startCash: Long,
    val spent: Long,
    val transfers: List<CTransfer>,
    val related: List<CExpense>,
    val pending: List<CPending>
)

/** View of expenses / pending / cash after applying 已當場還錢, used before the owner's phone ingests. */
data class ResolvedBooks(
    val expenses: List<CExpense>,
    val pending: List<CPending>,
    val cashDelta: Map<String, Long> // added to remaining cash (positive = more cash on hand)
)

object SharedView {
    fun repaidTag(method: String) = if (method == "CASH") "已還 💵" else "已還（轉帳）"
    fun repaidLine(payerName: String, forName: String, title: String) = "$payerName 幫 $forName · $title · 已還"

    fun resolve(expenses: List<CExpense>, pending: List<CPending>, repaid: List<CRepaidItem>): ResolvedBooks {
        if (repaid.isEmpty()) return ResolvedBooks(expenses, pending, emptyMap())
        val repaidItemIds = repaid.map { it.itemId }.toSet()
        val cashDelta = mutableMapOf<String, Long>()
        fun add(id: String, d: Long) { if (id.isNotEmpty() && d != 0L) cashDelta[id] = (cashDelta[id] ?: 0L) + d }
        val rewritten = expenses.map { e ->
            val r = repaid.firstOrNull { it.expenseId != null && it.expenseId == e.id } ?: return@map e
            e.copy(payerId = r.forId, method = r.repayMethod, shares = mapOf(r.forId to e.amountCents))
        }
        val keptPending = pending.filter { it.itemId.isEmpty() || it.itemId !in repaidItemIds }
        val synthetic = mutableListOf<CPending>()
        for (r in repaid) {
            val expExists = r.expenseId != null && expenses.any { it.id == r.expenseId }
            val label = repaidLine(r.payerName, r.forName, r.title)
            if (expExists) {
                if (r.repayMethod == "CASH") {
                    add(r.payerId, r.amountCents)
                    add(r.forId, -r.amountCents)
                }
            } else {
                synthetic += CPending(r.forId, r.title, r.amountCents, r.repayMethod, 0L, r.forId, r.itemId, label)
                if (r.repayMethod == "CASH") {
                    if (r.originalMethod != "CASH") add(r.payerId, r.amountCents)
                } else if (r.originalMethod == "CASH") {
                    add(r.payerId, -r.amountCents)
                }
            }
        }
        return ResolvedBooks(rewritten, keptPending + synthetic, cashDelta)
    }

    fun summary(
        meId: String,
        members: List<CMember>,
        expenses: List<CExpense>,
        pending: List<CPending>,
        repaid: List<CRepaidItem> = emptyList()
    ): MySummary {
        val books = resolve(expenses, pending, repaid)
        val me = members.firstOrNull { it.id == meId }
        val myPending = books.pending.filter { it.memberId == meId || it.forId == meId }
        val pendingCash = books.pending.filter { it.memberId == meId && it.method == "CASH" }.sumOf { it.amountCents }
        val spent = books.expenses.sumOf { it.shares[meId] ?: 0L } + books.pending.filter { it.forId == meId }.sumOf { it.amountCents }
        return MySummary(
            remainingCash = (me?.cashCents ?: 0L) - pendingCash + (books.cashDelta[meId] ?: 0L),
            startCash = me?.startCents ?: 0L,
            spent = spent,
            transfers = transfers(members, books.expenses).filter { it.fromId == meId || it.toId == meId },
            related = books.expenses.filter { it.payerId == meId || (it.shares[meId] ?: 0L) > 0 }.sortedByDescending { it.at },
            pending = myPending
        )
    }

    fun transfers(members: List<CMember>, expenses: List<CExpense>): List<CTransfer> {
        val ids = members.map { it.id }
        val idx = ids.withIndex().associate { (i, id) -> id to (i + 1).toLong() }
        val paid = mutableMapOf<Long, Long>()
        val share = mutableMapOf<Long, Long>()
        expenses.forEach { e ->
            idx[e.payerId]?.let { paid[it] = (paid[it] ?: 0) + e.amountCents }
            e.shares.forEach { (mid, c) -> idx[mid]?.let { share[it] = (share[it] ?: 0) + c } }
        }
        val back = idx.entries.associate { (k, v) -> v to k }
        return SplitMath.minimizeTransfers(SplitMath.nets(paid, share, idx.values))
            .map { CTransfer(back.getValue(it.fromMemberId), back.getValue(it.toMemberId), it.amountCents) }
    }

    private const val CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    fun newInviteCode(rnd: java.util.Random = java.security.SecureRandom()): String =
        (1..6).map { CODE_CHARS[rnd.nextInt(CODE_CHARS.length)] }.joinToString("")
    fun displayCode(code: String) = if (code.length == 6) code.substring(0, 4) + "-" + code.substring(4) else code
    fun normalizeCode(input: String) = input.uppercase().filter { it.isLetterOrDigit() }
}
