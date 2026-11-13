package tw.tripbook.data

import kotlinx.coroutines.flow.Flow
import tw.tripbook.domain.*

class Repository(private val db: AppDatabase) {
    private val trips get() = db.trips()
    private val members get() = db.members()
    private val expenses get() = db.expenses()
    private val cash get() = db.cash()
    private val shopping get() = db.shopping()

    fun observeTrips(): Flow<List<TripEntity>> = trips.observeTrips()
    fun observeTrip(id: Long) = trips.observe(id)
    fun observeMembers(tripId: Long) = members.observeForTrip(tripId)
    fun observeExpenses(tripId: Long) = expenses.observeForTrip(tripId)
    fun observeMemberCash(tripId: Long, memberId: Long) = cash.observeForMember(tripId, memberId)
    fun observePoolCash(tripId: Long) = cash.observeForPool(tripId)

    suspend fun getTrip(id: Long) = trips.get(id)
    suspend fun listMembers(tripId: Long) = members.listForTrip(tripId)
    suspend fun listExpenses(tripId: Long) = expenses.listForTrip(tripId)

    suspend fun createTrip(
        name: String, place: String, currency: String,
        startDate: String, endDate: String,
        drafts: List<MemberDraft>, meIndex: Int = 0,
        poolStarting: Long = 0
    ): Long {
        val tripId = trips.insert(
            TripEntity(
                name = name, place = place, currency = currency,
                startDate = startDate, endDate = endDate,
                poolCashCents = poolStarting,
                poolStartingCashCents = poolStarting
            )
        )
        drafts.forEachIndexed { i, d ->
            val start = d.startingCash.coerceAtLeast(0)
            members.insert(
                MemberEntity(
                    tripId = tripId,
                    name = d.name.trim(),
                    cashCents = start,
                    startingCashCents = start,
                    isMe = i == meIndex,
                    colorIndex = i % 6
                )
            )
        }
        return tripId
    }

    suspend fun updateTrip(trip: TripEntity) = trips.update(trip)
    suspend fun deleteTrip(id: Long) = trips.delete(id)

    /** @return true if deleted; false if missing or still used in expenses. */
    suspend fun tryDeleteMember(memberId: Long): Boolean {
        val m = members.get(memberId) ?: return false
        val mems = members.listForTrip(m.tripId)
        if (mems.size <= 1) return false
        val exps = expenses.listForTrip(m.tripId)
        if (exps.any { it.payerMemberId == memberId }) return false
        val splits = expenses.splitsForTrip(m.tripId)
        if (splits.any { it.memberId == memberId }) return false
        members.delete(memberId)
        return true
    }

    suspend fun addMember(tripId: Long, name: String, startingCash: Long = 0): Long {
        val count = members.listForTrip(tripId).size
        val start = startingCash.coerceAtLeast(0)
        return members.insert(
            MemberEntity(
                tripId = tripId, name = name.trim(),
                cashCents = start, startingCashCents = start,
                colorIndex = count % 6
            )
        )
    }

    /** Update brought/starting cash shown as 初始現金; also reset remaining if never spent relative to old start. */
    suspend fun setStartingCash(memberId: Long, starting: Long) {
        val m = members.get(memberId) ?: return
        val s = starting.coerceAtLeast(0)
        val spent = (m.startingCashCents - m.cashCents).coerceAtLeast(0)
        val remaining = (s - spent).coerceAtLeast(0)
        members.update(m.copy(startingCashCents = s, cashCents = remaining))
    }

    suspend fun setMemberCashDirect(memberId: Long, newCashCents: Long, note: String = "手動調整") {
        val m = members.get(memberId) ?: return
        val delta = newCashCents - m.cashCents
        if (delta == 0L) return
        members.update(m.copy(cashCents = newCashCents))
        cash.insert(
            CashMovementEntity(
                tripId = m.tripId,
                memberId = memberId,
                direction = if (delta > 0) CashDirection.IN.name else CashDirection.OUT.name,
                kind = CashKind.OTHER.name,
                amountCents = kotlin.math.abs(delta),
                note = note
            )
        )
    }

    suspend fun setPoolCashDirect(tripId: Long, newPoolCents: Long, holderId: Long?, note: String = "手動調整公款") {
        val t = trips.get(tripId) ?: return
        val delta = newPoolCents - t.poolCashCents
        trips.update(t.copy(poolCashCents = newPoolCents, poolHolderMemberId = holderId ?: t.poolHolderMemberId))
        if (delta == 0L) return
        cash.insert(
            CashMovementEntity(
                tripId = tripId,
                memberId = null,
                direction = if (delta > 0) CashDirection.IN.name else CashDirection.OUT.name,
                kind = CashKind.OTHER.name,
                amountCents = kotlin.math.abs(delta),
                note = note
            )
        )
    }

    /** Personal or pool cash IN/OUT that is NOT a shared expense. */
    suspend fun addCashMovement(
        tripId: Long,
        memberId: Long?, // null = pool
        direction: CashDirection,
        kind: CashKind,
        amountCents: Long,
        note: String
    ) {
        require(amountCents > 0)
        if (memberId == null) {
            val t = trips.get(tripId) ?: return
            val next = when (direction) {
                CashDirection.IN -> CashLedger.credit(t.poolCashCents, amountCents)
                CashDirection.OUT -> CashLedger.debit(t.poolCashCents, amountCents)
            }
            trips.update(t.copy(poolCashCents = next))
        } else {
            val m = members.get(memberId) ?: return
            val next = when (direction) {
                CashDirection.IN -> CashLedger.credit(m.cashCents, amountCents)
                CashDirection.OUT -> CashLedger.debit(m.cashCents, amountCents)
            }
            members.update(m.copy(cashCents = next))
        }
        cash.insert(
            CashMovementEntity(
                tripId = tripId,
                memberId = memberId,
                direction = direction.name,
                kind = kind.name,
                amountCents = amountCents,
                note = note
            )
        )
    }

    suspend fun getExpense(id: Long) = expenses.get(id)
    suspend fun splitsForExpense(expenseId: Long) = expenses.splitsFor(expenseId)

    suspend fun addExpense(
        tripId: Long,
        title: String,
        amountCents: Long,
        category: ExpenseCategory,
        payerMemberId: Long,
        method: PaymentMethod,
        participantIds: List<Long>,
        note: String = "",
        receiptPath: String? = null,
        occurredAt: Long = System.currentTimeMillis()
    ): Long {
        require(amountCents >= 0)
        require(participantIds.isNotEmpty()) { "至少一位分攤" }
        require(payerMemberId > 0) { "付款人必填" }
        val effectiveMethod = if (method == PaymentMethod.POOL) PaymentMethod.CASH else method
        val splits = SplitMath.equalSplit(amountCents, participantIds)
        val id = expenses.insert(
            ExpenseEntity(
                tripId = tripId,
                title = title,
                amountCents = amountCents,
                category = category.name,
                payerMemberId = payerMemberId,
                paymentMethod = effectiveMethod.name,
                note = note,
                receiptPath = receiptPath,
                occurredAt = occurredAt
            )
        )
        expenses.insertSplits(splits.map { ExpenseSplitEntity(id, it.memberId, it.amountCents) })
        applyCashForExpense(tripId, id, payerMemberId, effectiveMethod, amountCents, title)
        return id
    }

    /** Reverse old cash effect, rewrite expense + splits, apply new cash. */
    suspend fun updateExpense(
        expenseId: Long,
        title: String,
        amountCents: Long,
        category: ExpenseCategory,
        payerMemberId: Long,
        method: PaymentMethod,
        participantIds: List<Long>,
        note: String = "",
        receiptPath: String? = null,
        occurredAt: Long
    ) {
        require(amountCents >= 0)
        require(participantIds.isNotEmpty())
        require(payerMemberId > 0)
        val old = expenses.get(expenseId) ?: return
        val effectiveMethod = if (method == PaymentMethod.POOL) PaymentMethod.CASH else method
        // reverse old
        reverseCashOnly(old)
        cash.deleteForExpense(expenseId)
        expenses.deleteSplits(expenseId)
        val splits = SplitMath.equalSplit(amountCents, participantIds)
        expenses.update(
            old.copy(
                title = title,
                amountCents = amountCents,
                category = category.name,
                payerMemberId = payerMemberId,
                paymentMethod = effectiveMethod.name,
                note = note,
                receiptPath = receiptPath ?: old.receiptPath,
                occurredAt = occurredAt
            )
        )
        expenses.insertSplits(splits.map { ExpenseSplitEntity(expenseId, it.memberId, it.amountCents) })
        applyCashForExpense(old.tripId, expenseId, payerMemberId, effectiveMethod, amountCents, title)
    }

    private suspend fun reverseCashOnly(e: ExpenseEntity) {
        val method = runCatching { PaymentMethod.valueOf(e.paymentMethod) }.getOrDefault(PaymentMethod.CARD)
        when (method) {
            PaymentMethod.CASH -> {
                if (e.payerMemberId <= 0) return
                val m = members.get(e.payerMemberId) ?: return
                val (next, _) = CashLedger.reverseExpensePayment(m.cashCents, 0, e.amountCents, PaymentMethod.CASH)
                members.update(m.copy(cashCents = next))
            }
            PaymentMethod.POOL -> {
                val t = trips.get(e.tripId) ?: return
                val (_, next) = CashLedger.reverseExpensePayment(0, t.poolCashCents, e.amountCents, PaymentMethod.POOL)
                trips.update(t.copy(poolCashCents = next))
            }
            else -> Unit
        }
    }

    private suspend fun applyCashForExpense(
        tripId: Long, expenseId: Long, payerMemberId: Long,
        method: PaymentMethod, amountCents: Long, title: String
    ) {
        when (method) {
            PaymentMethod.CASH -> {
                val m = members.get(payerMemberId) ?: return
                val (next, _) = CashLedger.applyExpensePayment(m.cashCents, 0, amountCents, PaymentMethod.CASH)
                members.update(m.copy(cashCents = next))
                cash.insert(
                    CashMovementEntity(
                        tripId = tripId, memberId = payerMemberId,
                        direction = CashDirection.OUT.name, kind = CashKind.EXPENSE.name,
                        amountCents = amountCents, note = "分帳：$title", relatedExpenseId = expenseId
                    )
                )
            }
            PaymentMethod.POOL -> {
                val t = trips.get(tripId) ?: return
                val (_, nextPool) = CashLedger.applyExpensePayment(0, t.poolCashCents, amountCents, PaymentMethod.POOL)
                trips.update(t.copy(poolCashCents = nextPool))
                cash.insert(
                    CashMovementEntity(
                        tripId = tripId, memberId = null,
                        direction = CashDirection.OUT.name, kind = CashKind.EXPENSE.name,
                        amountCents = amountCents, note = "公款分帳：$title", relatedExpenseId = expenseId
                    )
                )
            }
            else -> Unit
        }
    }

    suspend fun deleteExpense(expenseId: Long) {
        val e = expenses.get(expenseId) ?: return
        shopping.unlinkExpense(expenseId)
        reverseCashOnly(e)
        cash.deleteForExpense(expenseId)
        expenses.deleteSplits(expenseId)
        expenses.delete(expenseId)
    }

    // ---- 我要買 ----
    fun observeShopping(tripId: Long) = shopping.observeForTrip(tripId)

    suspend fun addShoppingItem(tripId: Long, name: String, qty: Int, note: String): Long =
        shopping.insert(ShoppingItemEntity(tripId = tripId, name = name.trim(), qty = qty.coerceAtLeast(1), note = note.trim()))

    suspend fun updateShoppingItem(id: Long, name: String, qty: Int, note: String) {
        val it = shopping.get(id) ?: return
        shopping.update(it.copy(name = name.trim(), qty = qty.coerceAtLeast(1), note = note.trim()))
    }

    suspend fun deleteShoppingItem(id: Long) = shopping.delete(id)

    /** Bought: records a personal expense for 本人 (cash deducts remaining cash; card does not). */
    suspend fun markBought(itemId: Long, amountCents: Long, method: PaymentMethod): Long? {
        val item = shopping.get(itemId) ?: return null
        val mems = members.listForTrip(item.tripId)
        val me = mems.firstOrNull { it.isMe } ?: mems.firstOrNull() ?: return null
        item.expenseId?.let { deleteExpense(it) }
        val title = ShoppingText.expenseTitle(item.name, item.qty)
        val eid = addExpense(item.tripId, title, amountCents, ExpenseCategory.SHOP, me.id, method, listOf(me.id))
        shopping.update(item.copy(done = true, expenseId = eid))
        return eid
    }

    /** Untick: removes the linked expense (and gives the cash back). */
    suspend fun unmarkBought(itemId: Long) {
        val item = shopping.get(itemId) ?: return
        item.expenseId?.let { deleteExpense(it) }
        shopping.get(itemId)?.let { shopping.update(it.copy(done = false, expenseId = null)) }
    }

    suspend fun computeBalances(tripId: Long): List<MemberBalance> {
        val mems = members.listForTrip(tripId)
        val exps = expenses.listForTrip(tripId)
        val splits = expenses.splitsForTrip(tripId)
        val paid = mutableMapOf<Long, Long>()
        val share = mutableMapOf<Long, Long>()
        mems.forEach { paid[it.id] = 0; share[it.id] = 0 }
        exps.forEach { e ->
            if (e.payerMemberId > 0) paid[e.payerMemberId] = (paid[e.payerMemberId] ?: 0) + e.amountCents
        }
        splits.forEach { s ->
            share[s.memberId] = (share[s.memberId] ?: 0) + s.shareCents
        }
        return SplitMath.nets(paid, share, mems.map { it.id })
    }

    suspend fun computeTransfers(tripId: Long): List<Transfer> =
        SplitMath.minimizeTransfers(computeBalances(tripId))

    suspend fun markSettled(tripId: Long, settled: Boolean) {
        val t = trips.get(tripId) ?: return
        trips.update(t.copy(settled = settled))
    }

    suspend fun cashTimeline(tripId: Long, memberId: Long?): List<CashTimelineEntry> {
        val rows = if (memberId == null) cash.listForPool(tripId) else cash.listForMember(tripId, memberId)
        // Reconstruct starting balance: current - sum(IN) + sum(OUT) for chronological after
        var start = if (memberId == null) {
            trips.get(tripId)?.poolCashCents ?: 0L
        } else {
            members.get(memberId)?.cashCents ?: 0L
        }
        // Walk backward to find starting
        for (r in rows.asReversed()) {
            start = when (CashDirection.valueOf(r.direction)) {
                CashDirection.IN -> start - r.amountCents
                CashDirection.OUT -> start + r.amountCents
            }
        }
        var bal = start
        return rows.map { r ->
            bal = when (CashDirection.valueOf(r.direction)) {
                CashDirection.IN -> CashLedger.credit(bal, r.amountCents)
                CashDirection.OUT -> CashLedger.debit(bal, r.amountCents)
            }
            CashTimelineEntry(
                id = r.id,
                direction = CashDirection.valueOf(r.direction),
                kind = CashKind.valueOf(r.kind),
                amountCents = r.amountCents,
                note = r.note,
                occurredAt = r.occurredAt,
                balanceAfterCents = bal,
                relatedExpenseId = r.relatedExpenseId
            )
        }.asReversed() // newest first for UI
    }

    /** Amounts are whole TWD (NT$1 = 1). Field names keep *Cents for Room stability. */
    suspend fun seedDemoIfEmpty() {
        if (trips.listTrips().isNotEmpty()) return
        val tripId = createTrip(
            name = "東京三日遊", place = "日本東京", currency = "TWD",
            startDate = "2026-11-01", endDate = "2026-11-03",
            drafts = listOf(
                MemberDraft("宏威", 8000),
                MemberDraft("小美", 5000),
                MemberDraft("阿明", 6000)
            ),
            meIndex = 0
        )
        val mems = members.listForTrip(tripId)
        val hw = mems[0].id; val xm = mems[1].id; val am = mems[2].id
        fun day(d: Int, h: Int = 12) = java.time.LocalDateTime.of(2026, 11, d, h, 0)
            .atZone(java.time.ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli()

        addExpense(tripId, "新宿飯店", 6000, ExpenseCategory.STAY, hw, PaymentMethod.CARD, listOf(hw, xm, am), occurredAt = day(1, 15))
        addExpense(tripId, "一蘭拉麵", 1200, ExpenseCategory.FOOD, xm, PaymentMethod.CASH, listOf(hw, xm, am), occurredAt = day(1, 19))
        addExpense(tripId, "機場快線", 900, ExpenseCategory.TRANSIT, am, PaymentMethod.CASH, listOf(hw, xm, am), occurredAt = day(1, 10))
        addExpense(tripId, "晴空塔門票", 2400, ExpenseCategory.TICKET, hw, PaymentMethod.CARD, listOf(hw, xm, am), occurredAt = day(2, 11))
        addExpense(tripId, "築地午餐", 1500, ExpenseCategory.FOOD, am, PaymentMethod.CASH, listOf(hw, xm, am), occurredAt = day(2, 13))
        addExpense(tripId, "伴手禮", 1800, ExpenseCategory.SHOP, xm, PaymentMethod.CASH, listOf(hw, xm), occurredAt = day(2, 16))
        addExpense(tripId, "地鐵一日券", 600, ExpenseCategory.TRANSIT, hw, PaymentMethod.CASH, listOf(hw, xm, am), occurredAt = day(2, 9))
        addExpense(tripId, "早餐咖啡", 900, ExpenseCategory.FOOD, xm, PaymentMethod.CASH, listOf(hw, xm, am), occurredAt = day(3, 8))
        addExpense(tripId, "機場接送", 1200, ExpenseCategory.TRANSIT, am, PaymentMethod.TRANSFER, listOf(hw, xm, am), occurredAt = day(3, 14))
        addExpense(tripId, "藥妝採購", 2100, ExpenseCategory.SHOP, hw, PaymentMethod.CARD, listOf(hw, xm, am), occurredAt = day(3, 12))

    }
}
