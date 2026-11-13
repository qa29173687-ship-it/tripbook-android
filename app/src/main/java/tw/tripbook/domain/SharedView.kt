package tw.tripbook.domain

/** Pure models + math for the shared (cloud) trip, used by companions' read-only view. */
data class CMember(val id: String, val name: String, val startCents: Long, val cashCents: Long, val isOwner: Boolean)

data class CExpense(
    val id: String, val title: String, val amountCents: Long, val payerId: String,
    val method: String, val at: Long, val shares: Map<String, Long>
)

/** A companion purchase not yet recorded by the owner's phone. */
/** memberId = who paid; forId = whose item / whose share. */
data class CPending(val memberId: String, val title: String, val amountCents: Long, val method: String, val at: Long, val forId: String = memberId)

data class CTransfer(val fromId: String, val toId: String, val amountCents: Long)

data class MySummary(
    val remainingCash: Long,
    val startCash: Long,
    val spent: Long,
    val transfers: List<CTransfer>,
    val related: List<CExpense>,
    val pending: List<CPending>
)

object SharedView {
    fun summary(meId: String, members: List<CMember>, expenses: List<CExpense>, pending: List<CPending>): MySummary {
        val me = members.firstOrNull { it.id == meId }
        val myPending = pending.filter { it.memberId == meId || it.forId == meId }
        val pendingCash = pending.filter { it.memberId == meId && it.method == "CASH" }.sumOf { it.amountCents }
        val spent = expenses.sumOf { it.shares[meId] ?: 0L } + pending.filter { it.forId == meId }.sumOf { it.amountCents }
        return MySummary(
            remainingCash = (me?.cashCents ?: 0L) - pendingCash,
            startCash = me?.startCents ?: 0L,
            spent = spent,
            transfers = transfers(members, expenses).filter { it.fromId == meId || it.toId == meId },
            related = expenses.filter { it.payerId == meId || (it.shares[meId] ?: 0L) > 0 }.sortedByDescending { it.at },
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
