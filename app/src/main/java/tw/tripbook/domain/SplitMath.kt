package tw.tripbook.domain

object SplitMath {
    /** Equal split of totalCents among participants; leftover cents go to earliest ids. */
    fun equalSplit(totalCents: Long, participantIds: List<Long>): List<SplitShare> {
        require(totalCents >= 0) { "amount must be >= 0" }
        if (participantIds.isEmpty()) return emptyList()
        val n = participantIds.size
        val base = totalCents / n
        var rem = totalCents % n
        return participantIds.map { id ->
            val extra = if (rem > 0) 1L else 0L
            if (rem > 0) rem--
            SplitShare(id, base + extra)
        }
    }

    fun perPersonShare(totalCents: Long, splitterCount: Int): Long {
        if (splitterCount <= 0) return 0
        return totalCents / splitterCount
    }

    fun nets(
        paidByMember: Map<Long, Long>,
        shareByMember: Map<Long, Long>,
        allMemberIds: Collection<Long>
    ): List<MemberBalance> {
        return allMemberIds.distinct().map { id ->
            val paid = paidByMember[id] ?: 0L
            val share = shareByMember[id] ?: 0L
            MemberBalance(id, paid, share, paid - share)
        }
    }

    /**
     * Minimize number of transfers: greedy match largest debtor to largest creditor.
     * net > 0 means should receive; net < 0 means owes.
     */
    fun minimizeTransfers(balances: List<MemberBalance>): List<Transfer> {
        data class Node(val id: Long, var amount: Long)
        val creditors = balances.filter { it.netCents > 0 }
            .map { Node(it.memberId, it.netCents) }
            .sortedByDescending { it.amount }
            .toMutableList()
        val debtors = balances.filter { it.netCents < 0 }
            .map { Node(it.memberId, -it.netCents) }
            .sortedByDescending { it.amount }
            .toMutableList()
        val out = mutableListOf<Transfer>()
        var i = 0; var j = 0
        while (i < debtors.size && j < creditors.size) {
            val d = debtors[i]; val c = creditors[j]
            val pay = minOf(d.amount, c.amount)
            if (pay > 0) out += Transfer(d.id, c.id, pay)
            d.amount -= pay; c.amount -= pay
            if (d.amount == 0L) i++
            if (c.amount == 0L) j++
        }
        return out
    }
}
